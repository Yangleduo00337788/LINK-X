package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImWsFrame;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百炼 qwen-audio WebSocket 桥：Electron/浏览器经 IM WebSocket（8081）↔ LinkX ↔ DashScope。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateDashScopeWsBridge {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final String CALL_KEY_PREFIX = "linkmate:voice_call:";

    private final LinkMateRealtimeClient realtimeClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final Map<String, BridgeContext> bridges = new ConcurrentHashMap<>();

    public void attachImClient(Long userId, String callId, Channel imChannel) {
        if (!StringUtils.hasText(callId)) {
            throw new CustomException(400, "callId 不能为空");
        }
        if (imChannel == null || !imChannel.isActive()) {
            throw new CustomException(400, "IM WebSocket 未连接");
        }
        String trimmedCallId = callId.trim();
        validateCallOwnership(userId, trimmedCallId);

        BridgeContext existing = bridges.remove(trimmedCallId);
        if (existing != null) {
            closeUpstreamQuietly(existing.upstream);
        }

        String wsUrl = realtimeClient.buildDashScopeWebSocketUrl(realtimeClient.requireConfigPublic());
        String apiKey = realtimeClient.resolveDashScopeApiKey();
        String workspaceId = realtimeClient.resolveDashScopeWorkspaceId();
        String voice = resolveCallVoice(trimmedCallId);
        String instructions = resolveCallInstructions(trimmedCallId);

        BridgeContext context = new BridgeContext(trimmedCallId, userId, imChannel, voice, instructions);
        bridges.put(trimmedCallId, context);
        imChannel.closeFuture().addListener(f -> close(trimmedCallId));

        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey);
        if (StringUtils.hasText(workspaceId)) {
            builder.header("X-DashScope-WorkSpace", workspaceId);
        }

        log.info("LinkMate DashScope ws bridging callId={} url={}", trimmedCallId, abbreviateUrl(wsUrl));
        builder.buildAsync(URI.create(wsUrl), new UpstreamListener(context))
                .whenComplete((upstream, error) -> {
                    if (error != null) {
                        log.error("LinkMate DashScope ws upstream connect failed callId={}", trimmedCallId, error);
                        bridges.remove(trimmedCallId, context);
                        pushEvent(imChannel, errorPayload("连接百炼 Realtime 失败"));
                    } else {
                        context.upstream = upstream;
                        log.info("LinkMate DashScope ws upstream connected callId={}", trimmedCallId);
                    }
                });
    }

    public void forwardFromClient(String callId, String payload) {
        if (!StringUtils.hasText(callId) || !StringUtils.hasText(payload)) {
            return;
        }
        BridgeContext context = bridges.get(callId.trim());
        if (context == null || context.upstream == null) {
            return;
        }
        context.upstream.sendText(payload, true);
    }

    public void close(String callId) {
        if (!StringUtils.hasText(callId)) {
            return;
        }
        BridgeContext context = bridges.remove(callId.trim());
        if (context == null) {
            return;
        }
        closeUpstreamQuietly(context.upstream);
    }

    private void validateCallOwnership(Long userId, String callId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(CALL_KEY_PREFIX + callId);
        if (fields == null || fields.isEmpty()) {
            throw new CustomException(404, "通话不存在或已结束");
        }
        String owner = String.valueOf(fields.getOrDefault("userId", ""));
        if (!String.valueOf(userId).equals(owner)) {
            throw new CustomException(403, "无权操作该通话");
        }
    }

    private String resolveCallVoice(String callId) {
        Object voice = redisTemplate.opsForHash().get(CALL_KEY_PREFIX + callId, "voice");
        return voice != null ? String.valueOf(voice) : "longanqian";
    }

    private String resolveCallInstructions(String callId) {
        Object instructions = redisTemplate.opsForHash().get(CALL_KEY_PREFIX + callId, "instructions");
        return instructions != null ? String.valueOf(instructions) : "";
    }

    private void pushEvent(Channel channel, Object data) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        try {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("linkmate_voice_event");
            frame.setData(data);
            channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(frame)));
        } catch (Exception ex) {
            log.warn("LinkMate voice push to IM failed", ex);
        }
    }

    private Object errorPayload(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "error");
        node.put("message", message);
        return node;
    }

    private static void closeUpstreamQuietly(WebSocket upstream) {
        if (upstream == null) {
            return;
        }
        try {
            upstream.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static String abbreviateUrl(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) + "?..." : url;
    }

    private final class UpstreamListener implements WebSocket.Listener {

        private final BridgeContext context;
        private final StringBuilder textBuffer = new StringBuilder();

        private UpstreamListener(BridgeContext context) {
            this.context = context;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (!last) {
                webSocket.request(1);
                return null;
            }
            String text = textBuffer.toString();
            textBuffer.setLength(0);
            forwardToClient(text);
            // 仅做 session.update；开场白由客户端在 session.updated 后触发，避免双边重复 response.create
            if (text.contains("\"type\":\"session.created\"") || text.contains("session.created")) {
                String update = realtimeClient.buildSessionUpdateJson(context.voice, context.instructions);
                webSocket.sendText(update, true);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            bridges.remove(context.callId, context);
            pushEvent(context.imChannel, Map.of(
                    "type", "session.closed",
                    "code", statusCode,
                    "reason", reason != null ? reason : ""
            ));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("LinkMate DashScope ws upstream error callId={}", context.callId, error);
            bridges.remove(context.callId, context);
            pushEvent(context.imChannel, errorPayload("百炼连接异常"));
        }

        private void forwardToClient(String text) {
            try {
                Object data = objectMapper.readTree(text);
                pushEvent(context.imChannel, data);
            } catch (Exception ex) {
                pushEvent(context.imChannel, Map.of("type", "raw", "payload", text));
            }
        }
    }

    private static final class BridgeContext {
        private final String callId;
        private final Long userId;
        private final Channel imChannel;
        private final String voice;
        private final String instructions;
        private volatile WebSocket upstream;

        private BridgeContext(String callId, Long userId, Channel imChannel, String voice, String instructions) {
            this.callId = callId;
            this.userId = userId;
            this.imChannel = imChannel;
            this.voice = voice;
            this.instructions = instructions;
        }
    }
}
