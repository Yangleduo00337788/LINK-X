package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.dto.LinkMateGroupReplyDTO;
import com.linkx.server.controller.dto.LinkMateImContextDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.AiChatMessage;
import com.linkx.server.entity.AiChatSession;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.AiChatMessageMapper;
import com.linkx.server.mapper.AiChatSessionMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.LinkMateService;
import com.linkx.server.service.linkmate.LinkMateConstants;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmMessage;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmResult;
import com.linkx.server.service.linkmate.LinkMateLlmClient.StreamResult;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration SSE_TIMEOUT = Duration.ofMinutes(5);
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "linkmate-stream");
        t.setDaemon(true);
        return t;
    });

    private final LinkxProperties linkxProperties;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final LinkMateLlmClient llmClient;
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
    public List<LinkMateMessageVO> listMessages(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        List<AiChatMessage> messages = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("session_id", sessionId)
                        .orderBy("create_time", true)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
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
        List<LlmMessage> context = buildLlmContext(session.getId(), dto.getImContext());
        checkDailyLimit(userId, estimatePromptTokens(context));

        LlmResult result = llmClient.chat(context, Boolean.TRUE.equals(dto.getDeepThinking()));
        recordTokenUsage(userId, result.totalTokens());
        AiChatMessage assistant = saveAssistantMessage(userId, session, result.content(), result.totalTokens(), null, null, null);
        if (!regenerate) {
            maybeUpdateTitle(session, userMessage);
        }
        return toMessageVO(assistant);
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
        List<LlmMessage> context = buildLlmContext(session.getId(), dto.getImContext());
        checkDailyLimit(userId, estimatePromptTokens(context));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        Long sessionId = session.getId();
        Long rollbackUserMessageId = userMessageId;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));

        STREAM_EXECUTOR.execute(() -> {
            long streamStartedAt = System.currentTimeMillis();
            AtomicLong reasoningEndedAt = new AtomicLong(0);
            try {
                java.util.Map<String, Object> startPayload = new java.util.LinkedHashMap<>();
                startPayload.put("sessionId", String.valueOf(sessionId));
                if (rollbackUserMessageId != null) {
                    startPayload.put("userMessageId", String.valueOf(rollbackUserMessageId));
                }
                sendSse(emitter, "start", startPayload);
                boolean deepThinking = Boolean.TRUE.equals(dto.getDeepThinking())
                        && linkxProperties.getLinkmate().isReasoningSupported();
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
                        cancelled::get);
                if (result.cancelled() || cancelled.get()) {
                    if (rollbackUserMessageId != null) {
                        messageMapper.deleteById(rollbackUserMessageId);
                    }
                    emitter.complete();
                    return;
                }
                recordTokenUsage(userId, result.totalTokens());
                int responseDurationMs = (int) Math.max(1, System.currentTimeMillis() - streamStartedAt);
                Integer reasoningDurationMs = null;
                if (StringUtils.hasText(result.reasoning())) {
                    long reasoningEnd = reasoningEndedAt.get() > 0
                            ? reasoningEndedAt.get()
                            : System.currentTimeMillis();
                    reasoningDurationMs = (int) Math.max(1, reasoningEnd - streamStartedAt);
                }
                String reasoningContent = StringUtils.hasText(result.reasoning()) ? result.reasoning() : null;
                AiChatMessage assistant = saveAssistantMessage(
                        userId,
                        session,
                        result.content(),
                        result.totalTokens(),
                        reasoningContent,
                        responseDurationMs,
                        reasoningDurationMs);
                if (!regenerate) {
                    maybeUpdateTitle(session, userMessage);
                }
                sendSse(emitter, "done", Map.of(
                        "messageId", String.valueOf(assistant.getId()),
                        "sessionId", String.valueOf(sessionId),
                        "totalTokens", String.valueOf(result.totalTokens())));
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
        checkDailyLimit(userId, estimatePromptTokens(context));

        LlmResult result = llmClient.chat(context, false);
        recordTokenUsage(userId, result.totalTokens());
        return chatService.postLinkMateImMessage(dto.getConversationId(), result.content());
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
        checkDailyLimit(userId, estimatePromptTokens(context));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        Long conversationId = dto.getConversationId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(ex -> cancelled.set(true));

        STREAM_EXECUTOR.execute(() -> {
            try {
                sendSse(emitter, "start", Map.of("conversationId", String.valueOf(conversationId)));
                StreamResult result = llmClient.streamChat(
                        context,
                        false,
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
                recordTokenUsage(userId, result.totalTokens());
                MessageVO message = chatService.postLinkMateImMessage(conversationId, result.content());
                imMessagePushService.pushToConversationMembers(message, LinkMateConstants.BOT_SENDER_ID, null);
                sendSse(emitter, "done", Map.of(
                        "messageId", String.valueOf(message.getId()),
                        "conversationId", String.valueOf(conversationId),
                        "totalTokens", String.valueOf(result.totalTokens())));
                emitter.complete();
            } catch (CustomException ex) {
                sendError(emitter, ex.getMessage());
            } catch (Exception ex) {
                log.error("LinkMate IM stream error conversationId={}", conversationId, ex);
                sendError(emitter, "AI 服务请求失败");
            }
        });

        return emitter;
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
        return conversation;
    }

    private List<LlmMessage> buildImMentionLlmContext(
            Long conversationId,
            Long userId,
            ImConversation conversation,
            String question) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        String systemPrompt = cfg.getSystemPrompt();
        if (!StringUtils.hasText(systemPrompt)) {
            systemPrompt = "你是「灵伴」（LinkMate），LinkX 企业即时通讯平台的智能伙伴。"
                    + "你负责陪伴用户完成对话、知识检索、任务执行和各类 AI 助手功能。"
                    + "回答请简洁、专业、友好，使用用户使用的语言回复。";
        }
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));

        String groupTitle = StringUtils.hasText(conversation.getName())
                ? conversation.getName().trim()
                : (conversation.getType() == ImConversation.TYPE_GROUP ? "群聊" : "单聊");
        StringBuilder groupCtx = new StringBuilder();
        if (conversation.getType() == ImConversation.TYPE_GROUP) {
            groupCtx.append("你正在群聊「").append(groupTitle).append("」中被 @提及。");
            groupCtx.append("你的回复将作为一条群消息发送给所有群成员，请简洁、专业、友好。");
        } else {
            groupCtx.append("你正在与用户的单聊「").append(groupTitle).append("」中被 @提及。");
            groupCtx.append("你的回复将作为一条单聊消息发送给对方，请简洁、专业、友好。");
        }
        groupCtx.append("使用提问者使用的语言回复。\n");
        groupCtx.append("最近消息：\n");

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
            if (!ImMessage.TYPE_TEXT.equals(msg.getType()) && !ImMessage.TYPE_LOCATION.equals(msg.getType())) {
                continue;
            }
            if (!StringUtils.hasText(msg.getContent())) {
                continue;
            }
            String senderName;
            if (LinkMateConstants.BOT_SENDER_ID.equals(msg.getSenderId())) {
                senderName = LinkMateConstants.BOT_NICKNAME;
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
            groupCtx.append(prefix).append("：").append(msg.getContent().trim()).append('\n');
        }

        messages.add(new LlmMessage("system", groupCtx.toString().trim()));
        messages.add(new LlmMessage("user", question));
        return messages;
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

    private List<LlmMessage> buildLlmContext(Long sessionId, LinkMateImContextDTO imContext) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        String systemPrompt = cfg.getSystemPrompt();
        if (!StringUtils.hasText(systemPrompt)) {
            systemPrompt = "你是「灵伴」（LinkMate），LinkX 企业即时通讯平台的智能伙伴。"
                    + "你负责陪伴用户完成对话、知识检索、任务执行和各类 AI 助手功能。"
                    + "回答请简洁、专业、友好，使用用户使用的语言回复。";
        }
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));

        String imPreamble = formatImContext(imContext);
        if (StringUtils.hasText(imPreamble)) {
            messages.add(new LlmMessage("system", imPreamble));
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
            messages.add(new LlmMessage(msg.getRole(), msg.getContent()));
        }
        return messages;
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

    private void checkDailyLimit(Long userId, int estimatedTokens) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        int limit = cfg.getDailyTokenLimit();
        if (limit <= 0) {
            return;
        }
        int used = getDailyTokenUsed(userId);
        if (used + estimatedTokens > limit) {
            throw new CustomException(429, "今日灵伴使用额度已用尽，请明天再试");
        }
    }

    private void recordTokenUsage(Long userId, int tokens) {
        if (tokens <= 0) {
            return;
        }
        String key = dailyTokenKey(userId);
        Long total = redisTemplate.opsForValue().increment(key, tokens);
        if (total != null && total == tokens) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
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
