package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.dto.LinkMateClientContextDTO;
import com.linkx.server.controller.dto.LinkMateGroupReplyDTO;
import com.linkx.server.controller.dto.LinkMateImContextDTO;
import com.linkx.server.controller.dto.LinkMateTranslateDTO;
import com.linkx.server.controller.dto.LinkMateVoiceCallHangupDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import com.linkx.server.controller.vo.LinkMateTranslateVO;
import com.linkx.server.controller.vo.LinkMateTranscribeVO;
import com.linkx.server.controller.vo.LinkMateVoiceCallStartVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.AiChatMessage;
import com.linkx.server.entity.AiChatSession;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.ChatService;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.AiChatMessageMapper;
import com.linkx.server.mapper.AiChatSessionMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.service.LinkMateService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.linkx.server.service.linkmate.LinkMateAgentTools;
import com.linkx.server.service.linkmate.LinkMateImReplyFormatter;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.service.linkmate.LinkMatePromptTemplate;
import com.linkx.server.service.linkmate.LinkMateDashScopeWsBridge;
import com.linkx.server.service.linkmate.LinkMateRealtimeClient;
import com.linkx.server.service.linkmate.LinkMateSttClient;
import com.linkx.server.service.linkmate.LinkMateSttClient.TranscribeResult;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmMessage;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmResult;
import com.linkx.server.service.linkmate.LinkMateLlmClient.StreamResult;
import com.linkx.server.service.linkmate.LinkMateLlmClient.ToolCall;
import com.linkx.server.service.linkmate.LinkMateConstants;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.linkx.server.service.linkmate.LinkMateLlmClient.StreamDeltaHandlers;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateServiceImpl implements LinkMateService {

    private static final int HISTORY_LIMIT = 20;
    private static final int MESSAGE_PAGE_DEFAULT = 50;
    private static final int MESSAGE_PAGE_MAX = 100;
    private static final long DAILY_TOKEN_TTL_SECONDS = Duration.ofDays(2).getSeconds();
    /** 灵伴语音通话会话 Redis TTL */
    private static final Duration VOICE_CALL_TTL = Duration.ofMinutes(30);
    /** 发起通话时预扣额度（估算） */
    private static final int VOICE_CALL_RESERVE_TOKENS = 3000;
    /** 按通话分钟结算的估算 token（Realtime 音频计费粗估） */
    private static final int VOICE_CALL_TOKENS_PER_MINUTE = 2500;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration SSE_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_CONCURRENT_STREAMS_PER_USER = 2;
    private static final Duration STREAM_SLOT_TTL = Duration.ofMinutes(10);
    private static final int STREAM_POOL_MAX = 32;
    private static final int STREAM_POOL_QUEUE = 64;
    private static final long GROUP_REPLY_BUBBLE_DELAY_MS = 350L;
    private static final int VOICE_TRANSCRIBE_MAX_SECONDS = 60;
    private static final ExecutorService STREAM_EXECUTOR = new ThreadPoolExecutor(
            0,
            STREAM_POOL_MAX,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(STREAM_POOL_QUEUE),
            r -> {
                Thread t = new Thread(r, "linkmate-stream");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private static final String RESERVE_DAILY_TOKENS_LUA = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local amount = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            if limit <= 0 then return 1 end
            if amount <= 0 then return tonumber(redis.call('GET', key) or '0') end
            local current = tonumber(redis.call('GET', key) or '0')
            if current + amount > limit then return -1 end
            local newVal = redis.call('INCRBY', key, amount)
            if newVal == amount then redis.call('EXPIRE', key, ttl) end
            return newVal
            """;

    private static final String ADJUST_DAILY_TOKENS_LUA = """
            local key = KEYS[1]
            local delta = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            if delta == 0 then return tonumber(redis.call('GET', key) or '0') end
            local newVal = redis.call('INCRBY', key, delta)
            if newVal < 0 then
                redis.call('SET', key, '0')
                newVal = 0
            end
            if redis.call('TTL', key) < 0 then redis.call('EXPIRE', key, ttl) end
            return newVal
            """;

    private static final String RELEASE_DAILY_TOKENS_LUA = """
            local key = KEYS[1]
            local amount = tonumber(ARGV[1])
            if amount <= 0 then return tonumber(redis.call('GET', key) or '0') end
            local newVal = redis.call('DECRBY', key, amount)
            if newVal <= 0 then
                redis.call('DEL', key)
                return 0
            end
            return newVal
            """;

    private static final DefaultRedisScript<Long> RESERVE_DAILY_TOKENS_SCRIPT =
            new DefaultRedisScript<>(RESERVE_DAILY_TOKENS_LUA, Long.class);
    private static final DefaultRedisScript<Long> ADJUST_DAILY_TOKENS_SCRIPT =
            new DefaultRedisScript<>(ADJUST_DAILY_TOKENS_LUA, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_DAILY_TOKENS_SCRIPT =
            new DefaultRedisScript<>(RELEASE_DAILY_TOKENS_LUA, Long.class);

    private final LinkxProperties linkxProperties;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final LinkMateLlmClient llmClient;
    private final LinkMateAgentTools linkMateAgentTools;
    private final LinkMateSttClient sttClient;
    private final LinkMateRealtimeClient realtimeClient;
    private final LinkMateDashScopeWsBridge dashScopeWsBridge;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ImMessagePushService imMessagePushService;
    private final ImConversationMapper conversationMapper;
    private final ImMessageRepository imMessageRepository;
    private final SysUserMapper sysUserMapper;

    @Override
    public LinkMateStatusVO status(Long userId) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        boolean enabled = cfg.isEnabled() && StringUtils.hasText(cfg.getApiKey());
        return LinkMateStatusVO.builder()
                .enabled(enabled)
                .model(cfg.getModel())
                .dailyTokenLimit(cfg.getDailyTokenLimit())
                .dailyTokenUsed(getDailyTokenUsed(userId))
                .deepThinkingSupported(cfg.isReasoningSupported())
                .voiceCallSupported(enabled && realtimeClient.isConfigured())
                .agentEnabled(enabled && cfg.isAgentEnabled())
                .build();
    }

    @Override
    public List<LinkMateSessionVO> listSessions(Long userId) {
        List<AiChatSession> sessions = sessionMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .orderBy("update_time", false)
                        .limit(100)
        );
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LinkMateSessionVO createSession(Long userId) {
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title("新对话")
                .build();
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        AiChatSession session = requireSession(userId, sessionId);
        sessionMapper.deleteById(session.getId());
    }

    @Override
    @Transactional
    public LinkMateSessionVO renameSession(Long userId, Long sessionId, String title) {
        AiChatSession session = requireSession(userId, sessionId);
        String trimmed = trimMessage(title);
        if (!StringUtils.hasText(trimmed)) {
            throw new CustomException(400, "对话标题不能为空");
        }
        if (trimmed.length() > 80) {
            trimmed = trimmed.substring(0, 80);
        }
        session.setTitle(trimmed);
        touchSession(session);
        return toSessionVO(session);
    }

    @Override
    public List<LinkMateMessageVO> listMessages(Long userId, Long sessionId, Long beforeMessageId, int limit) {
        requireSession(userId, sessionId);
        int pageSize = Math.min(Math.max(limit > 0 ? limit : MESSAGE_PAGE_DEFAULT, 1), MESSAGE_PAGE_MAX);
        QueryWrapper query = QueryWrapper.create()
                .eq("session_id", sessionId)
                .orderBy("create_time", false)
                .limit(pageSize);
        if (beforeMessageId != null) {
            AiChatMessage before = messageMapper.selectOneById(beforeMessageId);
            if (before != null && sessionId.equals(before.getSessionId()) && before.getCreateTime() != null) {
                query.lt("create_time", before.getCreateTime());
            }
        }
        List<AiChatMessage> messages = messageMapper.selectListByQuery(query);
        List<LinkMateMessageVO> result = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            result.add(toMessageVO(messages.get(i)));
        }
        return result;
    }

    @Override
    @Transactional
    public LinkMateMessageVO chat(Long userId, LinkMateChatDTO dto) {
        validateChatDto(dto);
        boolean regenerate = Boolean.TRUE.equals(dto.getRegenerate());
        AiChatSession session;
        String userMessage;
        if (regenerate) {
            session = requireSession(userId, parseId(dto.getSessionId()));
            userMessage = prepareRegenerate(userId, session.getId(), dto.getRegenerateMessageId());
        } else {
            session = resolveSession(userId, dto.getSessionId(), dto.getMessage());
            userMessage = trimMessage(dto.getMessage());
            saveUserMessage(userId, session, userMessage);
        }
        List<LlmMessage> context = buildLlmContext(
                session.getId(), dto.getImContext(), resolveAgentMode(dto), dto.getClientContext());
        int estimatedTokens = estimatePromptTokens(context);
        int reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        try {
            LlmResult result = llmClient.chat(context, Boolean.TRUE.equals(dto.getDeepThinking()));
            finalizeDailyTokens(userId, reservedTokens, result.totalTokens());
            AiChatMessage assistant = saveAssistantMessage(userId, session, result.content(), result.totalTokens(), null, null, null);
            if (!regenerate) {
                maybeUpdateTitle(session, userMessage);
            }
            return toMessageVO(assistant);
        } catch (RuntimeException ex) {
            releaseDailyTokens(userId, reservedTokens);
            throw ex;
        }
    }

    @Override
    public SseEmitter streamChat(Long userId, LinkMateChatDTO dto) {
        validateChatDto(dto);
        boolean regenerate = Boolean.TRUE.equals(dto.getRegenerate());
        AiChatSession session;
        String userMessage;
        Long userMessageId = null;
        if (regenerate) {
            session = requireSession(userId, parseId(dto.getSessionId()));
            userMessage = prepareRegenerate(userId, session.getId(), dto.getRegenerateMessageId());
        } else {
            session = resolveSession(userId, dto.getSessionId(), dto.getMessage());
            userMessage = trimMessage(dto.getMessage());
            userMessageId = saveUserMessage(userId, session, userMessage).getId();
        }
        List<LlmMessage> context = buildLlmContext(
                session.getId(), dto.getImContext(), resolveAgentMode(dto), dto.getClientContext());
        int estimatedTokens = estimatePromptTokens(context);
        int reservedTokens;
        try {
            reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        } catch (CustomException ex) {
            if (userMessageId != null) {
                messageMapper.deleteById(userMessageId);
            }
            SseEmitter rejected = new SseEmitter(SSE_TIMEOUT.toMillis());
            sendError(rejected, ex.getMessage());
            return rejected;
        }

        try {
            acquireStreamSlot(userId);
        } catch (CustomException ex) {
            releaseDailyTokens(userId, reservedTokens);
            if (userMessageId != null) {
                messageMapper.deleteById(userMessageId);
            }
            SseEmitter rejected = new SseEmitter(SSE_TIMEOUT.toMillis());
            sendError(rejected, ex.getMessage());
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        Long sessionId = session.getId();
        Long rollbackUserMessageId = userMessageId;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));
        final int tokensReserved = reservedTokens;

        STREAM_EXECUTOR.execute(() -> {
            long streamStartedAt = System.currentTimeMillis();
            AtomicLong reasoningEndedAt = new AtomicLong(0);
            boolean tokensFinalized = false;
            try {
                java.util.Map<String, Object> startPayload = new java.util.LinkedHashMap<>();
                startPayload.put("sessionId", String.valueOf(sessionId));
                if (rollbackUserMessageId != null) {
                    startPayload.put("userMessageId", String.valueOf(rollbackUserMessageId));
                }
                sendSse(emitter, "start", startPayload);
                boolean deepThinking = Boolean.TRUE.equals(dto.getDeepThinking())
                        && linkxProperties.getLinkmate().isReasoningSupported();
                boolean agentMode = resolveAgentMode(dto);
                ArrayNode tools = agentMode ? linkMateAgentTools.buildToolsArray() : null;
                StreamResult result = llmClient.streamChat(
                        context,
                        deepThinking,
                        new StreamDeltaHandlers(
                                chunk -> {
                                    try {
                                        sendSse(emitter, "reasoning_delta", Map.of("content", chunk));
                                    } catch (Exception sendEx) {
                                        log.debug("LinkMate SSE client disconnected");
                                    }
                                },
                                chunk -> {
                                    if (reasoningEndedAt.get() == 0 && deepThinking) {
                                        reasoningEndedAt.set(System.currentTimeMillis());
                                    }
                                    try {
                                        sendSse(emitter, "delta", Map.of("content", chunk));
                                    } catch (Exception sendEx) {
                                        log.debug("LinkMate SSE client disconnected");
                                    }
                                }),
                        cancelled::get,
                        tools);
                if (result.cancelled() || cancelled.get()) {
                    if (rollbackUserMessageId != null) {
                        messageMapper.deleteById(rollbackUserMessageId);
                    }
                    emitter.complete();
                    return;
                }
                finalizeDailyTokens(userId, tokensReserved, result.totalTokens());
                tokensFinalized = true;
                int responseDurationMs = (int) Math.max(1, System.currentTimeMillis() - streamStartedAt);
                Integer reasoningDurationMs = null;
                if (StringUtils.hasText(result.reasoning())) {
                    long reasoningEnd = reasoningEndedAt.get() > 0
                            ? reasoningEndedAt.get()
                            : System.currentTimeMillis();
                    reasoningDurationMs = (int) Math.max(1, reasoningEnd - streamStartedAt);
                }
                String reasoningContent = StringUtils.hasText(result.reasoning()) ? result.reasoning() : null;
                String assistantContent = result.content();
                if (!StringUtils.hasText(assistantContent) && !result.toolCalls().isEmpty()) {
                    assistantContent = "正在为你执行操作…";
                }
                for (ToolCall toolCall : result.toolCalls()) {
                    java.util.Map<String, Object> toolPayload = new java.util.LinkedHashMap<>();
                    toolPayload.put("id", toolCall.id());
                    toolPayload.put("name", toolCall.name());
                    toolPayload.put("arguments", toolCall.argumentsJson());
                    sendSse(emitter, "tool_call", toolPayload);
                }
                AiChatMessage assistant = saveAssistantMessage(
                        userId,
                        session,
                        assistantContent,
                        result.totalTokens(),
                        reasoningContent,
                        responseDurationMs,
                        reasoningDurationMs);
                if (!regenerate) {
                    maybeUpdateTitle(session, userMessage);
                }
                java.util.Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
                donePayload.put("messageId", String.valueOf(assistant.getId()));
                donePayload.put("sessionId", String.valueOf(sessionId));
                donePayload.put("totalTokens", String.valueOf(result.totalTokens()));
                if (!result.toolCalls().isEmpty()) {
                    List<Map<String, Object>> actions = new ArrayList<>();
                    for (ToolCall toolCall : result.toolCalls()) {
                        actions.add(Map.of(
                                "id", toolCall.id(),
                                "name", toolCall.name(),
                                "arguments", toolCall.argumentsJson()));
                    }
                    donePayload.put("actions", actions);
                }
                sendSse(emitter, "done", donePayload);
                emitter.complete();
            } catch (CustomException ex) {
                if (rollbackUserMessageId != null) {
                    messageMapper.deleteById(rollbackUserMessageId);
                }
                sendError(emitter, ex.getMessage());
            } catch (Exception ex) {
                if (rollbackUserMessageId != null) {
                    messageMapper.deleteById(rollbackUserMessageId);
                }
                log.error("LinkMate stream error sessionId={}", sessionId, ex);
                sendError(emitter, "AI 服务请求失败");
            } finally {
                if (!tokensFinalized) {
                    releaseDailyTokens(userId, tokensReserved);
                }
                releaseStreamSlot(userId);
            }
        });

        return emitter;
    }

    @Override
    @Transactional
    public MessageVO replyInImChat(Long userId, LinkMateGroupReplyDTO dto) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        ImConversation conversation = requireImMentionConversation(userId, dto.getConversationId());

        String question = trimMessage(dto.getQuestion());
        if (!StringUtils.hasText(question)) {
            throw new CustomException(400, "提问内容不能为空");
        }

        List<LlmMessage> context = buildImMentionLlmContext(dto.getConversationId(), userId, conversation, question);
        int estimatedTokens = estimatePromptTokens(context);
        int reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        try {
            LlmResult result = llmClient.chat(context, resolveDeepThinking(dto.getDeepThinking()));
            finalizeDailyTokens(userId, reservedTokens, result.totalTokens());
            List<MessageVO> posted = postFormattedImReplies(userId, conversation, dto.getConversationId(), result.content());
            return posted.get(posted.size() - 1);
        } catch (RuntimeException ex) {
            releaseDailyTokens(userId, reservedTokens);
            throw ex;
        }
    }

    @Override
    public SseEmitter streamReplyInImChat(Long userId, LinkMateGroupReplyDTO dto) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        ImConversation conversation = requireImMentionConversation(userId, dto.getConversationId());

        String question = trimMessage(dto.getQuestion());
        if (!StringUtils.hasText(question)) {
            throw new CustomException(400, "提问内容不能为空");
        }

        List<LlmMessage> context = buildImMentionLlmContext(dto.getConversationId(), userId, conversation, question);
        int estimatedTokens = estimatePromptTokens(context);
        int reservedTokens;
        try {
            reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        } catch (CustomException ex) {
            SseEmitter rejected = new SseEmitter(SSE_TIMEOUT.toMillis());
            sendError(rejected, ex.getMessage());
            return rejected;
        }

        try {
            acquireStreamSlot(userId);
        } catch (CustomException ex) {
            releaseDailyTokens(userId, reservedTokens);
            SseEmitter rejected = new SseEmitter(SSE_TIMEOUT.toMillis());
            sendError(rejected, ex.getMessage());
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        Long conversationId = dto.getConversationId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));
        final int tokensReserved = reservedTokens;

        STREAM_EXECUTOR.execute(() -> {
            boolean tokensFinalized = false;
            try {
                sendSse(emitter, "start", Map.of("conversationId", String.valueOf(conversationId)));
                boolean deepThinking = resolveDeepThinking(dto.getDeepThinking());
                StreamResult result = llmClient.streamChat(
                        context,
                        deepThinking,
                        new StreamDeltaHandlers(
                                chunk -> {
                                    try {
                                        sendSse(emitter, "reasoning_delta", Map.of("content", chunk));
                                    } catch (Exception sendEx) {
                                        log.debug("LinkMate IM SSE client disconnected");
                                    }
                                },
                                chunk -> {
                                    try {
                                        sendSse(emitter, "delta", Map.of("content", chunk));
                                    } catch (Exception sendEx) {
                                        log.debug("LinkMate IM SSE client disconnected");
                                    }
                                }),
                        cancelled::get);
                if (result.cancelled() || cancelled.get()) {
                    emitter.complete();
                    return;
                }
                if (!StringUtils.hasText(result.content())) {
                    sendError(emitter, "AI 未返回有效内容");
                    return;
                }
                finalizeDailyTokens(userId, tokensReserved, result.totalTokens());
                tokensFinalized = true;
                List<MessageVO> posted = postFormattedImReplies(userId, conversation, conversationId, result.content());
                MessageVO lastMessage = posted.get(posted.size() - 1);
                List<Map<String, String>> messageItems = new ArrayList<>();
                for (MessageVO vo : posted) {
                    messageItems.add(Map.of(
                            "id", String.valueOf(vo.getId()),
                            "content", vo.getContent() != null ? vo.getContent() : ""));
                }
                Map<String, Object> donePayload = new java.util.HashMap<>();
                donePayload.put("messageId", String.valueOf(lastMessage.getId()));
                donePayload.put("conversationId", String.valueOf(conversationId));
                donePayload.put("totalTokens", String.valueOf(result.totalTokens()));
                donePayload.put("messages", messageItems);
                sendSse(emitter, "done", donePayload);
                emitter.complete();
            } catch (CustomException ex) {
                sendError(emitter, ex.getMessage());
            } catch (Exception ex) {
                log.error("LinkMate IM stream error conversationId={}", conversationId, ex);
                sendError(emitter, "AI 服务请求失败");
            } finally {
                if (!tokensFinalized) {
                    releaseDailyTokens(userId, tokensReserved);
                }
                releaseStreamSlot(userId);
            }
        });

        return emitter;
    }

    @Override
    public LinkMateTranslateVO translate(Long userId, LinkMateTranslateDTO dto) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        String sourceText = trimMessage(dto.getText());
        if (!StringUtils.hasText(sourceText)) {
            throw new CustomException(400, "待翻译内容不能为空");
        }
        String targetLang = resolveTranslateTargetLang(dto.getTargetLang());
        List<LlmMessage> messages = List.of(
                new LlmMessage("system", LinkMatePromptTemplate.TRANSLATE_SYSTEM.getTemplate()),
                new LlmMessage("user", LinkMatePromptTemplate.TRANSLATE_USER.format(Map.of(
                        "targetLang", targetLang,
                        "text", sourceText
                )))
        );
        int estimatedTokens = estimatePromptTokens(messages);
        int reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        try {
            LlmResult result = llmClient.chat(messages, false);
            finalizeDailyTokens(userId, reservedTokens, result.totalTokens());
            String translated = trimMessage(result.content());
            if (!StringUtils.hasText(translated)) {
                throw new CustomException(502, "翻译结果为空，请重试");
            }
            return LinkMateTranslateVO.builder()
                    .translatedText(translated)
                    .targetLang(targetLang)
                    .build();
        } catch (RuntimeException ex) {
            releaseDailyTokens(userId, reservedTokens);
            throw ex;
        }
    }

    private String resolveTranslateTargetLang(String targetLang) {
        if (!StringUtils.hasText(targetLang)) {
            return "English";
        }
        String normalized = targetLang.trim().toLowerCase();
        return switch (normalized) {
            case "zh", "zh-cn", "zh_cn", "chinese" -> "简体中文";
            case "en", "en-us", "en_us", "english" -> "English";
            case "ja", "japanese" -> "日本語";
            case "ko", "korean" -> "한국어";
            default -> targetLang.trim();
        };
    }

    @Override
    public LinkMateTranscribeVO transcribeAudio(
            Long userId,
            byte[] audioBytes,
            String filename,
            String contentType,
            String languageHint) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new CustomException(400, "语音文件为空");
        }
        // 粗估：约每 50KB 对应 1 秒语音
        int estimatedSeconds = Math.max(1, audioBytes.length / (50 * 1024));
        if (estimatedSeconds > VOICE_TRANSCRIBE_MAX_SECONDS) {
            throw new CustomException(400, "语音过长，暂不支持转写");
        }
        int estimatedTokens = Math.max(200, estimatedSeconds * 20);
        int reservedTokens = reserveDailyTokens(userId, estimatedTokens);
        try {
            TranscribeResult result = sttClient.transcribe(
                    audioBytes,
                    StringUtils.hasText(filename) ? filename.trim() : "voice.webm",
                    StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                    normalizeSttLanguageHint(languageHint));
            finalizeDailyTokens(userId, reservedTokens, estimatedTokens);
            return LinkMateTranscribeVO.builder()
                    .text(result.text())
                    .language(result.language())
                    .build();
        } catch (CustomException ex) {
            releaseDailyTokens(userId, reservedTokens);
            throw ex;
        } catch (Exception ex) {
            releaseDailyTokens(userId, reservedTokens);
            log.error("LinkMate audio transcribe failed", ex);
            throw new CustomException(502, "语音转写失败");
        }
    }

    @Override
    public LinkMateVoiceCallStartVO startVoiceCall(Long userId) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        if (!realtimeClient.isConfigured()) {
            throw new CustomException(503, "灵伴语音通话未配置 Realtime");
        }
        String existing = redisTemplate.opsForValue().get(voiceCallUserKey(userId));
        if (StringUtils.hasText(existing)) {
            throw new CustomException(409, "已有进行中的灵伴语音通话");
        }

        int reservedTokens = reserveDailyTokens(userId, VOICE_CALL_RESERVE_TOKENS);
        String callId = UUID.randomUUID().toString().replace("-", "");
        try {
            String instructions = buildVoiceCallInstructions(cfg);
            LinkMateRealtimeClient.RealtimeProvider provider = realtimeClient.detectProvider(cfg);
            LinkMateRealtimeClient.ClientSecretResult secret;
            if (provider == LinkMateRealtimeClient.RealtimeProvider.DASHSCOPE) {
                if (LinkMateRealtimeClient.usesWebSocketBridge(cfg.getRealtimeModel())) {
                    secret = realtimeClient.createDashScopeWsSession(callId);
                } else {
                    secret = realtimeClient.createDashScopeProxySession(callId);
                }
            } else {
                secret = realtimeClient.createOpenAiClientSecret(userId, instructions);
            }

            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(voiceCallUserKey(userId), callId, VOICE_CALL_TTL);
            if (!Boolean.TRUE.equals(locked)) {
                throw new CustomException(409, "已有进行中的灵伴语音通话");
            }

            String callKey = voiceCallKey(callId);
            redisTemplate.opsForHash().putAll(callKey, Map.of(
                    "userId", String.valueOf(userId),
                    "startedAt", String.valueOf(System.currentTimeMillis()),
                    "reservedTokens", String.valueOf(reservedTokens),
                    "status", "active",
                    "voice", secret.voice(),
                    "instructions", instructions
            ));
            redisTemplate.expire(callKey, VOICE_CALL_TTL);

            return LinkMateVoiceCallStartVO.builder()
                    .callId(callId)
                    .ephemeralKey(secret.ephemeralKey())
                    .realtimeCallsUrl(secret.realtimeCallsUrl())
                    .model(secret.model())
                    .voice(secret.voice())
                    .peerNickname("灵伴 LinkMate")
                    .expiresAt(secret.expiresAtEpochSec())
                    .provider(mapVoiceCallProvider(secret.provider()))
                    .build();
        } catch (CustomException ex) {
            releaseDailyTokens(userId, reservedTokens);
            redisTemplate.delete(voiceCallUserKey(userId));
            redisTemplate.delete(voiceCallKey(callId));
            throw ex;
        } catch (Exception ex) {
            releaseDailyTokens(userId, reservedTokens);
            redisTemplate.delete(voiceCallUserKey(userId));
            redisTemplate.delete(voiceCallKey(callId));
            log.error("LinkMate voice call start failed", ex);
            throw new CustomException(502, "发起灵伴语音通话失败");
        }
    }

    @Override
    public void hangupVoiceCall(Long userId, LinkMateVoiceCallHangupDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCallId())) {
            throw new CustomException(400, "callId 不能为空");
        }
        String callId = dto.getCallId().trim();
        String callKey = voiceCallKey(callId);
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(callKey);
        if (fields == null || fields.isEmpty()) {
            redisTemplate.delete(voiceCallUserKey(userId));
            return;
        }
        String owner = String.valueOf(fields.getOrDefault("userId", ""));
        if (!String.valueOf(userId).equals(owner)) {
            throw new CustomException(403, "无权结束该通话");
        }

        int reserved = parseIntSafe(String.valueOf(fields.getOrDefault("reservedTokens", "0")), 0);
        long startedAt = parseLongSafe(String.valueOf(fields.getOrDefault("startedAt", "0")), 0L);
        int durationSec = dto.getDurationSec() != null && dto.getDurationSec() > 0
                ? dto.getDurationSec()
                : (startedAt > 0
                ? (int) Math.max(1, (System.currentTimeMillis() - startedAt) / 1000L)
                : 1);
        int minutes = Math.max(1, (int) Math.ceil(durationSec / 60.0));
        int actualTokens = Math.min(Math.max(minutes * VOICE_CALL_TOKENS_PER_MINUTE, 500), 200_000);
        finalizeDailyTokens(userId, reserved, actualTokens);

        dashScopeWsBridge.close(callId);
        redisTemplate.delete(callKey);
        String userKey = voiceCallUserKey(userId);
        String current = redisTemplate.opsForValue().get(userKey);
        if (callId.equals(current)) {
            redisTemplate.delete(userKey);
        }
    }

    @Override
    public String exchangeVoiceCallWebrtc(Long userId, String callId, String offerSdp) {
        if (!StringUtils.hasText(callId)) {
            throw new CustomException(400, "callId 不能为空");
        }
        String trimmedCallId = callId.trim();
        String callKey = voiceCallKey(trimmedCallId);
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(callKey);
        if (fields == null || fields.isEmpty()) {
            throw new CustomException(404, "通话不存在或已结束");
        }
        String owner = String.valueOf(fields.getOrDefault("userId", ""));
        if (!String.valueOf(userId).equals(owner)) {
            throw new CustomException(403, "无权操作该通话");
        }
        return realtimeClient.exchangeDashScopeSdp(offerSdp);
    }

    private String buildVoiceCallInstructions(LinkxProperties.LinkMate cfg) {
        String base = StringUtils.hasText(cfg.getSystemPrompt())
                ? cfg.getSystemPrompt().trim()
                : LinkMatePromptTemplate.DEFAULT_SYSTEM.getTemplate();
        return base + "\n\n你正在与用户进行实时语音通话。请用自然口语简短回应，避免长篇列表与复杂 Markdown。"
                + "用户打断时立即停下并倾听。默认使用用户正在说的语言回复。"
                + "用户接通后，请先说一句简短自然的中文问候并邀请对方开口说话。";
    }

    private static String mapVoiceCallProvider(LinkMateRealtimeClient.RealtimeProvider provider) {
        if (provider == LinkMateRealtimeClient.RealtimeProvider.DASHSCOPE_WS) {
            return "dashscope-ws";
        }
        return provider.name().toLowerCase();
    }

    private String voiceCallKey(String callId) {
        return "linkmate:voice_call:" + callId;
    }

    private String voiceCallUserKey(Long userId) {
        return "linkmate:voice_call:user:" + userId;
    }

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLongSafe(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String normalizeSttLanguageHint(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        return switch (language.trim().toLowerCase()) {
            case "zh", "zh-cn", "zh_cn" -> "zh";
            case "en", "en-us", "en_us" -> "en";
            case "ja", "japanese" -> "ja";
            case "ko", "korean" -> "ko";
            default -> language.trim().toLowerCase();
        };
    }

    private ImConversation requireImMentionConversation(Long userId, Long conversationId) {
        chatService.assertConversationMember(userId, conversationId);
        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        if (conversation.getType() != ImConversation.TYPE_GROUP
                && conversation.getType() != ImConversation.TYPE_PRIVATE) {
            throw new CustomException(400, "仅支持在群聊或单聊中 @灵伴");
        }
        if (conversation.getType() == ImConversation.TYPE_GROUP
                && (conversation.getLinkmateEnabled() == null || conversation.getLinkmateEnabled() == 0)) {
            throw new CustomException(403, "本群已关闭灵伴接入");
        }
        return conversation;
    }

    private String resolveSystemPrompt(LinkxProperties.LinkMate cfg) {
        if (cfg != null && StringUtils.hasText(cfg.getSystemPrompt())) {
            return cfg.getSystemPrompt().trim();
        }
        return LinkMatePromptTemplate.DEFAULT_SYSTEM.getTemplate();
    }

    private List<LlmMessage> buildImMentionLlmContext(
            Long conversationId,
            Long userId,
            ImConversation conversation,
            String question) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        String systemPrompt = resolveSystemPrompt(cfg);
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));

        String groupTitle = StringUtils.hasText(conversation.getName())
                ? conversation.getName().trim()
                : (conversation.getType() == ImConversation.TYPE_GROUP ? "群聊" : "单聊");
        StringBuilder groupCtx = new StringBuilder();
        if (conversation.getType() == ImConversation.TYPE_GROUP) {
            groupCtx.append(LinkMatePromptTemplate.IM_MENTION_GROUP_SCENE.format(
                    Map.of("conversationTitle", groupTitle)));
        } else {
            groupCtx.append(LinkMatePromptTemplate.IM_MENTION_PRIVATE_SCENE.format(
                    Map.of("conversationTitle", groupTitle)));
        }
        groupCtx.append(LinkMatePromptTemplate.IM_MENTION_SCENE_SUFFIX.getTemplate());

        List<ImMessage> recent = imMessageRepository.selectListByQuery(
                QueryWrapper.create()
                        .eq("conversation_id", conversationId)
                        .ne("type", ImMessage.TYPE_SYSTEM)
                        .ne("type", ImMessage.TYPE_RECALL)
                        .orderBy("id", false)
                        .limit(HISTORY_LIMIT)
        );
        recent.sort(Comparator.comparing(ImMessage::getId));

        Set<Long> senderIds = recent.stream()
                .map(ImMessage::getSenderId)
                .filter(id -> id != null && !LinkMateConstants.BOT_SENDER_ID.equals(id))
                .collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = senderIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectListByQuery(QueryWrapper.create().in("id", senderIds))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        for (ImMessage msg : recent) {
            String preview = formatImMessagePreview(msg);
            if (!StringUtils.hasText(preview)) {
                continue;
            }
            String senderName;
            if (LinkMateConstants.BOT_SENDER_ID.equals(msg.getSenderId())) {
                senderName = conversation.getType() == ImConversation.TYPE_GROUP
                        ? LinkMateConstants.GROUP_ASSISTANT_NICKNAME
                        : LinkMateConstants.BOT_NICKNAME;
            } else {
                SysUser sender = senderMap.get(msg.getSenderId());
                if (sender != null) {
                    senderName = StringUtils.hasText(sender.getNickname())
                            ? sender.getNickname()
                            : (StringUtils.hasText(sender.getUsername()) ? sender.getUsername() : "用户");
                } else {
                    senderName = "用户";
                }
            }
            String prefix = userId.equals(msg.getSenderId()) ? senderName + "（提问者）" : senderName;
            groupCtx.append(prefix).append("：").append(preview.trim()).append('\n');
        }

        messages.add(new LlmMessage("system", groupCtx.toString().trim()));
        messages.add(new LlmMessage("user", question));
        return messages;
    }

    private List<MessageVO> postFormattedImReplies(
            Long userId,
            ImConversation conversation,
            Long conversationId,
            String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            throw new CustomException(400, "回复内容不能为空");
        }
        boolean isGroup = conversation.getType() == ImConversation.TYPE_GROUP;
        List<String> parts;
        if (isGroup) {
            parts = LinkMateImReplyFormatter.splitGroupBubbles(rawContent);
            String senderName = resolveUserDisplayName(userId);
            parts = parts.stream()
                    .map(part -> LinkMateImReplyFormatter.withMentionPrefix(part, senderName))
                    .toList();
        } else {
            parts = List.of(rawContent.trim());
        }
        List<MessageVO> posted = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                try {
                    Thread.sleep(GROUP_REPLY_BUBBLE_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            MessageVO vo = chatService.postLinkMateImMessage(conversationId, parts.get(i));
            imMessagePushService.pushToConversationMembers(vo, LinkMateConstants.BOT_SENDER_ID, null);
            posted.add(vo);
        }
        return posted;
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "用户";
        }
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            return "用户";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return "用户";
    }

    private String formatImMessagePreview(ImMessage msg) {
        if (msg == null || ImMessage.TYPE_RECALL.equals(msg.getType()) || ImMessage.TYPE_SYSTEM.equals(msg.getType())) {
            return null;
        }
        String type = msg.getType();
        if (ImMessage.TYPE_TEXT.equals(type)) {
            return StringUtils.hasText(msg.getContent()) ? msg.getContent().trim() : null;
        }
        if (ImMessage.TYPE_LOCATION.equals(type)) {
            return StringUtils.hasText(msg.getContent())
                    ? "[位置] " + msg.getContent().trim()
                    : "[位置]";
        }
        if (ImMessage.TYPE_IMAGE.equals(type)) {
            return "[图片]";
        }
        if (ImMessage.TYPE_FILE.equals(type)) {
            if (StringUtils.hasText(msg.getFileName())) {
                return "[文件] " + msg.getFileName().trim();
            }
            return StringUtils.hasText(msg.getContent()) ? "[文件] " + msg.getContent().trim() : "[文件]";
        }
        if (ImMessage.TYPE_VOICE.equals(type)) {
            return "[语音]";
        }
        if (ImMessage.TYPE_RED_PACKET.equals(type)) {
            return StringUtils.hasText(msg.getFileName())
                    ? "[红包] " + msg.getFileName().trim()
                    : "[红包]";
        }
        if (ImMessage.TYPE_CONFERENCE.equals(type)) {
            return StringUtils.hasText(msg.getContent()) ? msg.getContent().trim() : "[会议]";
        }
        return StringUtils.hasText(msg.getContent()) ? msg.getContent().trim() : null;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            sendSse(emitter, "error", Map.of("message", message));
        } catch (Exception ignored) {
            // client gone
        }
        emitter.complete();
    }

    private void sendSse(SseEmitter emitter, String event, Map<String, Object> payload) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(event).data(payload));
    }

    private AiChatSession resolveSession(Long userId, String sessionIdStr, String firstMessage) {
        if (StringUtils.hasText(sessionIdStr)) {
            return requireSession(userId, parseId(sessionIdStr));
        }
        String title = abbreviateTitle(firstMessage);
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title(title)
                .build();
        sessionMapper.insert(session);
        return session;
    }

    private AiChatSession requireSession(Long userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", sessionId)
                        .eq("deleted", 0)
        );
        if (session == null) {
            throw new CustomException(404, "对话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new CustomException(403, "无权访问此对话");
        }
        return session;
    }

    private AiChatMessage saveUserMessage(Long userId, AiChatSession session, String content) {
        AiChatMessage msg = AiChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role("user")
                .content(trimMessage(content))
                .tokenCount(estimateTextTokens(content))
                .build();
        messageMapper.insert(msg);
        touchSession(session);
        return msg;
    }

    private AiChatMessage saveAssistantMessage(
            Long userId,
            AiChatSession session,
            String content,
            int tokens,
            String reasoningContent,
            Integer responseDurationMs,
            Integer reasoningDurationMs) {
        AiChatMessage msg = AiChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role("assistant")
                .content(content)
                .reasoningContent(StringUtils.hasText(reasoningContent) ? reasoningContent : null)
                .responseDurationMs(responseDurationMs)
                .reasoningDurationMs(reasoningDurationMs)
                .tokenCount(tokens)
                .build();
        messageMapper.insert(msg);
        touchSession(session);
        return msg;
    }

    private void touchSession(AiChatSession session) {
        session.setUpdateTime(new Date());
        sessionMapper.update(session);
    }

    private void maybeUpdateTitle(AiChatSession session, String userMessage) {
        if ("新对话".equals(session.getTitle())) {
            session.setTitle(abbreviateTitle(userMessage));
            sessionMapper.update(session);
        }
    }

    private boolean resolveAgentMode(LinkMateChatDTO dto) {
        return Boolean.TRUE.equals(dto.getAgentMode())
                && linkxProperties.getLinkmate().isAgentEnabled();
    }

    private List<LlmMessage> buildLlmContext(
            Long sessionId,
            LinkMateImContextDTO imContext,
            boolean agentMode,
            LinkMateClientContextDTO clientContext) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        String systemPrompt = resolveSystemPrompt(cfg);
        if (agentMode) {
            systemPrompt = systemPrompt + "\n\n" + linkMateAgentTools.agentSystemSuffix();
        }
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));

        String imPreamble = formatImContext(imContext);
        if (StringUtils.hasText(imPreamble)) {
            messages.add(new LlmMessage("system", imPreamble));
        }

        String clientPreamble = formatClientContext(clientContext);
        if (StringUtils.hasText(clientPreamble)) {
            messages.add(new LlmMessage("system", clientPreamble));
        }

        List<AiChatMessage> history = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("session_id", sessionId)
                        .orderBy("create_time", false)
                        .limit(HISTORY_LIMIT)
        );
        // 按时间正序送入模型
        for (int i = history.size() - 1; i >= 0; i--) {
            AiChatMessage msg = history.get(i);
            messages.add(new LlmMessage(msg.getRole(), formatHistoryMessageContent(msg)));
        }
        return messages;
    }

    private boolean resolveDeepThinking(Boolean requested) {
        return Boolean.TRUE.equals(requested)
                && linkxProperties.getLinkmate().isReasoningSupported();
    }

    private String formatHistoryMessageContent(AiChatMessage msg) {
        if (!"assistant".equals(msg.getRole()) || !StringUtils.hasText(msg.getReasoningContent())) {
            return msg.getContent();
        }
        return "[思考过程]\n" + msg.getReasoningContent().trim() + "\n[回答]\n" + msg.getContent();
    }

    private String formatImContext(LinkMateImContextDTO imContext) {
        if (imContext == null) {
            return null;
        }
        if (!StringUtils.hasText(imContext.getTitle()) && !StringUtils.hasText(imContext.getConversationId())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户当前正在 LinkX 中查看以下 IM 会话，可作为回答参考（勿编造未出现的消息）：\n");
        String title = StringUtils.hasText(imContext.getTitle()) ? imContext.getTitle().trim() : "未命名会话";
        boolean group = Boolean.TRUE.equals(imContext.getGroup());
        sb.append("会话：").append(title).append(group ? "（群聊）" : "（单聊）").append('\n');
        if (StringUtils.hasText(imContext.getConversationId())) {
            sb.append("会话ID：").append(imContext.getConversationId().trim()).append('\n');
        }
        if (imContext.getMessages() == null || imContext.getMessages().isEmpty()) {
            sb.append("最近消息：暂无已加载文本消息，请结合会话名称作答。\n");
            return sb.toString().trim();
        }
        sb.append("最近消息：\n");
        for (LinkMateImContextDTO.ImMessageItem item : imContext.getMessages()) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            String sender = StringUtils.hasText(item.getSender()) ? item.getSender().trim() : "未知";
            String time = StringUtils.hasText(item.getTime()) ? item.getTime().trim() : "";
            String prefix = Boolean.TRUE.equals(item.getSelf()) ? sender + "（我）" : sender;
            if (StringUtils.hasText(time)) {
                sb.append('[').append(time).append("] ");
            }
            sb.append(prefix).append("：").append(item.getContent().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatClientContext(LinkMateClientContextDTO clientContext) {
        if (clientContext == null) {
            return null;
        }
        if (!StringUtils.hasText(clientContext.getCurrentNav())
                && !StringUtils.hasText(clientContext.getCurrentSessionId())
                && !StringUtils.hasText(clientContext.getTodayDate())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户当前 LinkX 客户端状态：\n");
        if (StringUtils.hasText(clientContext.getTodayDate())) {
            sb.append("客户端本地今天：").append(clientContext.getTodayDate().trim()).append('\n');
        }
        if (StringUtils.hasText(clientContext.getCurrentNav())) {
            sb.append("当前页面：").append(clientContext.getCurrentNav().trim()).append('\n');
        }
        if (StringUtils.hasText(clientContext.getCurrentSessionTitle())) {
            sb.append("当前会话：").append(clientContext.getCurrentSessionTitle().trim()).append('\n');
        }
        if (StringUtils.hasText(clientContext.getCurrentSessionId())) {
            sb.append("当前会话ID：").append(clientContext.getCurrentSessionId().trim()).append('\n');
        }
        if (StringUtils.hasText(clientContext.getRecentSessions())) {
            sb.append("可操作的近期会话（格式：好友/群聊「名称」#会话ID）：")
                    .append(clientContext.getRecentSessions().trim())
                    .append('\n');
            sb.append("给某位好友发消息时优先使用好友单聊，不要误选名称中包含该好友的群聊；")
                    .append("请尽量填写 conversationId，或 name 配合 chatType=direct/group。")
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private void validateChatDto(LinkMateChatDTO dto) {
        if (Boolean.TRUE.equals(dto.getRegenerate())) {
            if (!StringUtils.hasText(dto.getSessionId())) {
                throw new CustomException(400, "重新生成需要指定会话");
            }
            if (!StringUtils.hasText(dto.getRegenerateMessageId())) {
                throw new CustomException(400, "请指定要重新生成的消息");
            }
            return;
        }
        if (!StringUtils.hasText(dto.getMessage())) {
            throw new CustomException(400, "消息内容不能为空");
        }
    }

    @Transactional
    protected String prepareRegenerate(Long userId, Long sessionId, String assistantMessageIdStr) {
        Long assistantId = parseId(assistantMessageIdStr);
        AiChatMessage assistant = messageMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", assistantId)
                        .eq("session_id", sessionId)
                        .eq("user_id", userId)
                        .eq("role", "assistant")
        );
        if (assistant == null) {
            throw new CustomException(404, "要重新生成的消息不存在");
        }
        AiChatMessage lastAssistant = messageMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("session_id", sessionId)
                        .eq("role", "assistant")
                        .orderBy("create_time", false)
                        .limit(1)
        );
        if (lastAssistant == null || !lastAssistant.getId().equals(assistantId)) {
            throw new CustomException(400, "只能重新生成最后一条助手回复");
        }
        AiChatMessage userMessage = messageMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("session_id", sessionId)
                        .eq("role", "user")
                        .lt("create_time", assistant.getCreateTime())
                        .orderBy("create_time", false)
                        .limit(1)
        );
        if (userMessage == null || !StringUtils.hasText(userMessage.getContent())) {
            throw new CustomException(400, "找不到对应的用户提问");
        }
        messageMapper.deleteById(assistantId);
        return userMessage.getContent();
    }

    private int reserveDailyTokens(Long userId, int estimatedTokens) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        int limit = cfg.getDailyTokenLimit();
        if (limit <= 0) {
            return 0;
        }
        int amount = Math.max(1, estimatedTokens);
        Long result = redisTemplate.execute(
                RESERVE_DAILY_TOKENS_SCRIPT,
                List.of(dailyTokenKey(userId)),
                String.valueOf(limit),
                String.valueOf(amount),
                String.valueOf(DAILY_TOKEN_TTL_SECONDS));
        if (result == null || result < 0) {
            throw new CustomException(429, "今日灵伴使用额度已用尽，请明天再试");
        }
        return amount;
    }

    private void finalizeDailyTokens(Long userId, int reserved, int actual) {
        if (reserved <= 0 && actual <= 0) {
            return;
        }
        if (actual <= 0) {
            releaseDailyTokens(userId, reserved);
            return;
        }
        int delta = actual - reserved;
        if (delta == 0) {
            return;
        }
        redisTemplate.execute(
                ADJUST_DAILY_TOKENS_SCRIPT,
                List.of(dailyTokenKey(userId)),
                String.valueOf(delta),
                String.valueOf(DAILY_TOKEN_TTL_SECONDS));
    }

    private void releaseDailyTokens(Long userId, int reserved) {
        if (reserved <= 0) {
            return;
        }
        redisTemplate.execute(
                RELEASE_DAILY_TOKENS_SCRIPT,
                List.of(dailyTokenKey(userId)),
                String.valueOf(reserved));
    }

    private void acquireStreamSlot(Long userId) {
        String key = streamSlotKey(userId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, STREAM_SLOT_TTL);
        }
        if (count != null && count > MAX_CONCURRENT_STREAMS_PER_USER) {
            redisTemplate.opsForValue().decrement(key);
            throw new CustomException(429, "灵伴请求过于频繁，请稍后再试");
        }
    }

    private void releaseStreamSlot(Long userId) {
        String key = streamSlotKey(userId);
        Long value = redisTemplate.opsForValue().decrement(key);
        if (value != null && value <= 0) {
            redisTemplate.delete(key);
        }
    }

    private String streamSlotKey(Long userId) {
        return "linkmate:active_stream:" + userId;
    }

    private int getDailyTokenUsed(Long userId) {
        String val = redisTemplate.opsForValue().get(dailyTokenKey(userId));
        if (!StringUtils.hasText(val)) {
            return 0;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String dailyTokenKey(Long userId) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        return "linkmate:daily_tokens:" + userId + ":" + day;
    }

    private LinkMateSessionVO toSessionVO(AiChatSession session) {
        return LinkMateSessionVO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .updateTime(formatTime(session.getUpdateTime()))
                .build();
    }

    private LinkMateMessageVO toMessageVO(AiChatMessage message) {
        return LinkMateMessageVO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .role(message.getRole())
                .content(message.getContent())
                .reasoningContent(message.getReasoningContent())
                .responseDurationMs(message.getResponseDurationMs())
                .reasoningDurationMs(message.getReasoningDurationMs())
                .createTime(formatTime(message.getCreateTime()))
                .build();
    }

    private String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        return TIME_FORMATTER.format(date.toInstant().atZone(ZoneId.of("Asia/Shanghai")));
    }

    private static String trimMessage(String content) {
        return content == null ? "" : content.trim();
    }

    private static String abbreviateTitle(String text) {
        String trimmed = trimMessage(text).replaceAll("\\s+", " ");
        if (trimmed.length() <= 40) {
            return StringUtils.hasText(trimmed) ? trimmed : "新对话";
        }
        return trimmed.substring(0, 40) + "...";
    }

    private static int estimateTextTokens(String text) {
        return Math.max(1, trimMessage(text).length() / 3);
    }

    private static int estimatePromptTokens(List<LlmMessage> messages) {
        int chars = 0;
        for (LlmMessage msg : messages) {
            chars += msg.content().length();
        }
        return Math.max(1, chars / 3);
    }

    private static Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "invalid session id");
        }
    }
}
