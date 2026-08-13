package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import com.linkx.server.entity.AiChatMessage;
import com.linkx.server.entity.AiChatSession;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.AiChatMessageMapper;
import com.linkx.server.mapper.AiChatSessionMapper;
import com.linkx.server.service.LinkMateService;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmMessage;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmResult;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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

    @Override
    public LinkMateStatusVO status(Long userId) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        boolean enabled = cfg.isEnabled() && StringUtils.hasText(cfg.getApiKey());
        return LinkMateStatusVO.builder()
                .enabled(enabled)
                .model(cfg.getModel())
                .dailyTokenLimit(cfg.getDailyTokenLimit())
                .dailyTokenUsed(getDailyTokenUsed(userId))
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
        AiChatSession session = resolveSession(userId, dto.getSessionId(), dto.getMessage());
        saveUserMessage(userId, session, dto.getMessage());
        List<LlmMessage> context = buildLlmContext(session.getId());
        checkDailyLimit(userId, estimatePromptTokens(context));

        LlmResult result = llmClient.chat(context);
        recordTokenUsage(userId, result.totalTokens());
        AiChatMessage assistant = saveAssistantMessage(userId, session, result.content(), result.totalTokens());
        maybeUpdateTitle(session, dto.getMessage());
        return toMessageVO(assistant);
    }

    @Override
    public SseEmitter streamChat(Long userId, LinkMateChatDTO dto) {
        AiChatSession session = resolveSession(userId, dto.getSessionId(), dto.getMessage());
        saveUserMessage(userId, session, dto.getMessage());
        List<LlmMessage> context = buildLlmContext(session.getId());
        checkDailyLimit(userId, estimatePromptTokens(context));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        Long sessionId = session.getId();
        String userMessage = dto.getMessage();

        STREAM_EXECUTOR.execute(() -> {
            try {
                sendSse(emitter, "start", Map.of("sessionId", String.valueOf(sessionId)));
                StringBuilder full = new StringBuilder();
                int tokens = llmClient.streamChat(context, chunk -> {
                    full.append(chunk);
                    try {
                        sendSse(emitter, "delta", Map.of("content", chunk));
                    } catch (Exception sendEx) {
                        log.debug("LinkMate SSE client disconnected");
                    }
                });
                recordTokenUsage(userId, tokens);
                AiChatMessage assistant = saveAssistantMessage(userId, session, full.toString(), tokens);
                maybeUpdateTitle(session, userMessage);
                sendSse(emitter, "done", Map.of(
                        "messageId", String.valueOf(assistant.getId()),
                        "sessionId", String.valueOf(sessionId)));
                emitter.complete();
            } catch (CustomException ex) {
                sendError(emitter, ex.getMessage());
            } catch (Exception ex) {
                log.error("LinkMate stream error sessionId={}", sessionId, ex);
                sendError(emitter, "AI 服务请求失败");
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> emitter.complete());
        return emitter;
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

    private void saveUserMessage(Long userId, AiChatSession session, String content) {
        AiChatMessage msg = AiChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role("user")
                .content(trimMessage(content))
                .tokenCount(estimateTextTokens(content))
                .build();
        messageMapper.insert(msg);
        touchSession(session);
    }

    private AiChatMessage saveAssistantMessage(Long userId, AiChatSession session, String content, int tokens) {
        AiChatMessage msg = AiChatMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role("assistant")
                .content(content)
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

    private List<LlmMessage> buildLlmContext(Long sessionId) {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        String systemPrompt = cfg.getSystemPrompt();
        if (!StringUtils.hasText(systemPrompt)) {
            systemPrompt = "你是「灵伴」（LinkMate），LinkX 企业即时通讯平台的智能伙伴。"
                    + "你负责陪伴用户完成对话、知识检索、任务执行和各类 AI 助手功能。"
                    + "回答请简洁、专业、友好，使用用户使用的语言回复。";
        }
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));

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
