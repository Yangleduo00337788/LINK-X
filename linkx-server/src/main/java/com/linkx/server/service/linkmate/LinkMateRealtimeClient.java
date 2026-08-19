package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * OpenAI Realtime：服务端签发 ephemeral client secret，浏览器直连 WebRTC。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateRealtimeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_MODEL = "gpt-realtime";
    private static final String DEFAULT_VOICE = "marin";

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public record ClientSecretResult(
            String ephemeralKey,
            String realtimeCallsUrl,
            String model,
            String voice,
            long expiresAtEpochSec
    ) {
    }

    public boolean isConfigured() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        // 显式配置了 Realtime Key；或单独写了 Realtime 基址并可用主 Key 回退
        if (StringUtils.hasText(cfg.getRealtimeApiKey())) {
            return true;
        }
        return StringUtils.hasText(cfg.getRealtimeBaseUrl())
                && StringUtils.hasText(cfg.getApiKey());
    }

    public ClientSecretResult createClientSecret(Long userId, String instructions) {
        LinkxProperties.LinkMate cfg = requireConfig();
        String apiKey = resolveRealtimeApiKey(cfg);
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(503, "灵伴语音通话未配置 Realtime API Key");
        }
        String baseUrl = normalizeBaseUrl(resolveRealtimeBaseUrl(cfg));
        String model = StringUtils.hasText(cfg.getRealtimeModel())
                ? cfg.getRealtimeModel().trim()
                : DEFAULT_MODEL;
        String voice = StringUtils.hasText(cfg.getRealtimeVoice())
                ? cfg.getRealtimeVoice().trim()
                : DEFAULT_VOICE;

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode session = root.putObject("session");
        session.put("type", "realtime");
        session.put("model", model);
        if (StringUtils.hasText(instructions)) {
            session.put("instructions", instructions.trim());
        }
        ObjectNode audio = session.putObject("audio");
        ObjectNode output = audio.putObject("output");
        output.put("voice", voice);

        String body;
        try {
            body = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new CustomException(500, "构建 Realtime 会话失败");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/realtime/client_secrets"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Safety-Identifier", safetyIdentifier(userId))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LinkMate Realtime client_secrets error status={} body={}",
                        response.statusCode(), abbreviate(response.body()));
                throw new CustomException(mapHttpStatus(response.statusCode()),
                        "创建语音会话失败：" + extractErrorMessage(response.body()));
            }
            JsonNode json = objectMapper.readTree(response.body());
            String ephemeral = firstText(json, "value");
            if (!StringUtils.hasText(ephemeral) && json.has("client_secret")) {
                ephemeral = firstText(json.get("client_secret"), "value");
            }
            if (!StringUtils.hasText(ephemeral)) {
                throw new CustomException(502, "Realtime 未返回临时密钥");
            }
            long expiresAt = 0L;
            JsonNode expiresNode = json.get("expires_at");
            if (expiresNode == null && json.has("client_secret")) {
                expiresNode = json.get("client_secret").get("expires_at");
            }
            if (expiresNode != null && expiresNode.isNumber()) {
                expiresAt = expiresNode.asLong();
            }
            return new ClientSecretResult(
                    ephemeral,
                    baseUrl + "/realtime/calls",
                    model,
                    voice,
                    expiresAt
            );
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LinkMate Realtime client_secrets failed", ex);
            throw new CustomException(502, "连接 Realtime 服务失败");
        }
    }

    private LinkxProperties.LinkMate requireConfig() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled()) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        return cfg;
    }

    private String resolveRealtimeApiKey(LinkxProperties.LinkMate cfg) {
        if (StringUtils.hasText(cfg.getRealtimeApiKey())) {
            return cfg.getRealtimeApiKey().trim();
        }
        return StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey().trim() : "";
    }

    private String resolveRealtimeBaseUrl(LinkxProperties.LinkMate cfg) {
        if (StringUtils.hasText(cfg.getRealtimeBaseUrl())) {
            return cfg.getRealtimeBaseUrl().trim();
        }
        if (StringUtils.hasText(cfg.getBaseUrl())) {
            return cfg.getBaseUrl().trim();
        }
        return "https://api.openai.com";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.endsWith("/v1")) {
            trimmed = trimmed + "/v1";
        }
        return trimmed;
    }

    private static String safetyIdentifier(Long userId) {
        String raw = "linkx-user-" + (userId != null ? userId : "anon") + "-" + UUID.randomUUID();
        return HexFormat.of().formatHex(raw.getBytes(StandardCharsets.UTF_8)).substring(0, Math.min(64, raw.length() * 2));
    }

    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "未知错误";
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode err = json.get("error");
            if (err != null) {
                String msg = firstText(err, "message");
                if (StringUtils.hasText(msg)) {
                    return msg;
                }
            }
            String msg = firstText(json, "message");
            if (StringUtils.hasText(msg)) {
                return msg;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return abbreviate(body);
    }

    private static String firstText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String text = node.get(field).asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static int mapHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return 503;
        }
        if (status == 429) {
            return 429;
        }
        if (status >= 400 && status < 500) {
            return 400;
        }
        return 502;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
