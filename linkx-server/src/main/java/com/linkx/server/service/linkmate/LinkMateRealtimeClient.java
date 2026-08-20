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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 灵伴 Realtime 语音：OpenAI（ephemeral + 浏览器直连）或百炼 DashScope（服务端 SDP 代理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateRealtimeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_OPENAI_MODEL = "gpt-realtime";
    private static final String DEFAULT_OPENAI_VOICE = "marin";

    public enum RealtimeProvider {
        OPENAI,
        /** 百炼 Omni 等 WebRTC SDP 交换 */
        DASHSCOPE,
        /** 百炼 qwen-audio 等 WebSocket 实时对话 */
        DASHSCOPE_WS,
        UNSUPPORTED
    }

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
            long expiresAtEpochSec,
            RealtimeProvider provider
    ) {
    }

    public boolean isConfigured() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled()) {
            return false;
        }
        RealtimeProvider provider = detectProvider(cfg);
        if (provider == RealtimeProvider.UNSUPPORTED) {
            return false;
        }
        if (!StringUtils.hasText(resolveRealtimeApiKey(cfg))) {
            return false;
        }
        // 必须显式配置 Realtime Key 或 Realtime 基址，避免 DeepSeek / 硅基主 Key 误亮按钮
        if (!StringUtils.hasText(cfg.getRealtimeApiKey()) && !StringUtils.hasText(cfg.getRealtimeBaseUrl())) {
            return false;
        }
        if (provider == RealtimeProvider.DASHSCOPE && !StringUtils.hasText(cfg.getRealtimeModel())) {
            return false;
        }
        return true;
    }

    public RealtimeProvider detectProvider(LinkxProperties.LinkMate cfg) {
        if (cfg == null) {
            return RealtimeProvider.UNSUPPORTED;
        }
        String explicitBase = normalizeForDetect(cfg.getRealtimeBaseUrl());
        if (isDashScopeUrl(explicitBase)) {
            return RealtimeProvider.DASHSCOPE;
        }
        if (isIncompatibleRealtimeUrl(explicitBase)) {
            return RealtimeProvider.UNSUPPORTED;
        }
        if (StringUtils.hasText(explicitBase) && isOpenAiRealtimeUrl(explicitBase)) {
            return RealtimeProvider.OPENAI;
        }
        if (StringUtils.hasText(cfg.getRealtimeApiKey()) && !StringUtils.hasText(explicitBase)) {
            return RealtimeProvider.OPENAI;
        }
        if (StringUtils.hasText(explicitBase)) {
            // 显式写了基址但非百炼/OpenAI，视为不兼容
            return RealtimeProvider.UNSUPPORTED;
        }
        return RealtimeProvider.UNSUPPORTED;
    }

    public ClientSecretResult createOpenAiClientSecret(Long userId, String instructions) {
        LinkxProperties.LinkMate cfg = requireConfig();
        if (detectProvider(cfg) != RealtimeProvider.OPENAI) {
            throw new CustomException(503, "当前 Realtime 配置不是 OpenAI 兼容接口");
        }
        String apiKey = resolveRealtimeApiKey(cfg);
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(503, "灵伴语音通话未配置 Realtime API Key");
        }
        String baseUrl = normalizeOpenAiBaseUrl(resolveRealtimeBaseUrl(cfg));
        String model = resolveOpenAiModel(cfg);
        String voice = resolveVoice(cfg);

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
                log.warn("LinkMate OpenAI client_secrets error status={} body={}",
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
                    expiresAt,
                    RealtimeProvider.OPENAI
            );
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LinkMate OpenAI client_secrets failed", ex);
            throw new CustomException(502, "连接 Realtime 服务失败");
        }
    }

    public ClientSecretResult createDashScopeWsSession(String callId) {
        if (!StringUtils.hasText(callId)) {
            throw new CustomException(400, "callId 不能为空");
        }
        LinkxProperties.LinkMate cfg = requireConfig();
        if (detectProvider(cfg) != RealtimeProvider.DASHSCOPE) {
            throw new CustomException(503, "当前 Realtime 配置不是百炼 DashScope");
        }
        String model = resolveDashScopeModel(cfg);
        validateDashScopeRealtimeModel(model);
        if (!usesWebSocketBridge(model)) {
            throw new CustomException(400, "当前模型应使用 WebRTC 接入，请使用 qwen3.5-omni-* 系列");
        }
        String voice = resolveDashScopeVoice(cfg);
        String streamPath = "im";
        return new ClientSecretResult(
                "",
                streamPath,
                model,
                voice,
                0L,
                RealtimeProvider.DASHSCOPE_WS
        );
    }

    public ClientSecretResult createDashScopeProxySession(String callId) {
        LinkxProperties.LinkMate cfg = requireConfig();
        if (detectProvider(cfg) != RealtimeProvider.DASHSCOPE) {
            throw new CustomException(503, "当前 Realtime 配置不是百炼 DashScope");
        }
        String model = resolveDashScopeModel(cfg);
        if (usesWebSocketBridge(model)) {
            throw new CustomException(400, "qwen-audio 模型请使用 WebSocket 接入，无需 WebRTC SDP");
        }
        String voice = resolveDashScopeVoice(cfg);
        String proxyPath = "/linkmate/voice-call/webrtc?callId=" + URLEncoder.encode(callId, StandardCharsets.UTF_8);
        return new ClientSecretResult(
                "",
                proxyPath,
                model,
                voice,
                0L,
                RealtimeProvider.DASHSCOPE
        );
    }

    public String exchangeDashScopeSdp(String offerSdp) {
        if (!StringUtils.hasText(offerSdp)) {
            throw new CustomException(400, "SDP offer 不能为空");
        }
        LinkxProperties.LinkMate cfg = requireConfig();
        if (detectProvider(cfg) != RealtimeProvider.DASHSCOPE) {
            throw new CustomException(503, "当前未配置百炼 Realtime");
        }
        String apiKey = resolveRealtimeApiKey(cfg);
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(503, "灵伴语音通话未配置 Realtime API Key");
        }
        String webrtcUrl = buildDashScopeWebrtcUrl(cfg);
        String model = resolveDashScopeModel(cfg);
        String workspaceId = extractWorkspaceId(resolveRealtimeBaseUrl(cfg));
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webrtcUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/sdp");
        if (StringUtils.hasText(workspaceId)) {
            requestBuilder.header("X-DashScope-WorkSpace", workspaceId);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(offerSdp.trim(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LinkMate DashScope webrtc error url={} status={} body={}",
                        abbreviateUrl(webrtcUrl), response.statusCode(), abbreviate(response.body()));
                throw new CustomException(mapHttpStatus(response.statusCode()),
                        "百炼 SDP 交换失败：" + extractDashScopeErrorMessage(response.statusCode(), response.body(), model));
            }
            String answer = response.body();
            if (!StringUtils.hasText(answer)) {
                throw new CustomException(502, "百炼未返回 SDP answer");
            }
            return answer.trim();
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LinkMate DashScope webrtc failed", ex);
            throw new CustomException(502, "连接百炼 Realtime 失败");
        }
    }

    private LinkxProperties.LinkMate requireConfig() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled()) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        return cfg;
    }

    public LinkxProperties.LinkMate requireConfigPublic() {
        return requireConfig();
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
        return "https://api.openai.com";
    }

    private static String resolveOpenAiModel(LinkxProperties.LinkMate cfg) {
        return StringUtils.hasText(cfg.getRealtimeModel())
                ? cfg.getRealtimeModel().trim()
                : DEFAULT_OPENAI_MODEL;
    }

    private static String resolveVoice(LinkxProperties.LinkMate cfg) {
        return StringUtils.hasText(cfg.getRealtimeVoice())
                ? cfg.getRealtimeVoice().trim()
                : DEFAULT_OPENAI_VOICE;
    }

    private static String normalizeOpenAiBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.endsWith("/v1")) {
            trimmed = trimmed + "/v1";
        }
        return trimmed;
    }

    public String buildDashScopeWebSocketUrl(LinkxProperties.LinkMate cfg) {
        String model = resolveDashScopeModel(cfg);
        String configuredBase = StringUtils.hasText(cfg.getRealtimeBaseUrl())
                ? cfg.getRealtimeBaseUrl().trim()
                : "";
        if (configuredBase.endsWith("/")) {
            configuredBase = configuredBase.substring(0, configuredBase.length() - 1);
        }
        String lower = configuredBase.toLowerCase();
        if (lower.contains("maas.aliyuncs.com") && hasWorkspaceSubdomain(lower)) {
            String host = extractMaasHost(configuredBase);
            if (StringUtils.hasText(host)) {
                return "wss://" + host + "/api-ws/v1/realtime?model="
                        + URLEncoder.encode(model, StandardCharsets.UTF_8);
            }
        }
        if (lower.contains("dashscope-intl")) {
            return "wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime?model="
                    + URLEncoder.encode(model, StandardCharsets.UTF_8);
        }
        return "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model="
                + URLEncoder.encode(model, StandardCharsets.UTF_8);
    }

    public String buildSessionUpdateJson(String voice, String instructions) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "session.update");
        ObjectNode session = root.putObject("session");
        session.putArray("modalities").add("text").add("audio");
        session.put("voice", StringUtils.hasText(voice) ? voice : "longanqian");
        session.put("input_audio_format", "pcm");
        session.put("output_audio_format", "pcm");
        if (StringUtils.hasText(instructions)) {
            session.put("instructions", instructions.trim());
        }
        ObjectNode turn = session.putObject("turn_detection");
        turn.put("type", "server_vad");
        turn.put("threshold", 0.5);
        turn.put("prefix_padding_ms", 500);
        turn.put("silence_duration_ms", 800);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new CustomException(500, "构建 session.update 失败");
        }
    }

    public String resolveDashScopeApiKey() {
        return resolveRealtimeApiKey(requireConfig());
    }

    public String resolveDashScopeWorkspaceId() {
        return extractWorkspaceId(resolveRealtimeBaseUrl(requireConfig()));
    }

    private String buildDashScopeWebrtcUrl(LinkxProperties.LinkMate cfg) {
        String model = resolveDashScopeModel(cfg);
        validateDashScopeRealtimeModel(model);

        String configuredBase = resolveRealtimeBaseUrl(cfg).trim();
        if (configuredBase.endsWith("/")) {
            configuredBase = configuredBase.substring(0, configuredBase.length() - 1);
        }
        String lower = configuredBase.toLowerCase();

        if (lower.contains("/webrtc/realtime")) {
            if (lower.contains("model=")) {
                return configuredBase;
            }
            return UriComponentsBuilder.fromUriString(configuredBase)
                    .queryParam("model", model)
                    .build(true)
                    .toUriString();
        }

        // Omni 等 WebRTC 模型：优先 Workspace 信令地址
        if (lower.contains("maas.aliyuncs.com") && hasWorkspaceSubdomain(lower)) {
            String base = configuredBase;
            if (!lower.endsWith("/api")) {
                if (lower.endsWith("/api/v1")) {
                    base = base.substring(0, base.length() - 3);
                } else {
                    base = base + "/api";
                }
            }
            return base + "/v1/webrtc/realtime?model=" + URLEncoder.encode(model, StandardCharsets.UTF_8);
        }

        if (lower.contains("dashscope-intl")) {
            return "https://dashscope-intl.aliyuncs.com/api/v1/webrtc/realtime?model="
                    + URLEncoder.encode(model, StandardCharsets.UTF_8);
        }

        return "https://dashscope.aliyuncs.com/api/v1/webrtc/realtime?model="
                + URLEncoder.encode(model, StandardCharsets.UTF_8);
    }

    private static String resolveDashScopeModel(LinkxProperties.LinkMate cfg) {
        return StringUtils.hasText(cfg.getRealtimeModel())
                ? cfg.getRealtimeModel().trim()
                : "qwen-audio-3.0-realtime-flash";
    }

    private static void validateDashScopeRealtimeModel(String model) {
        String lower = model.toLowerCase();
        if (lower.contains("gpt-realtime") || lower.startsWith("gpt-")) {
            throw new CustomException(400,
                    "百炼 Realtime 模型应使用 qwen-audio-3.0-realtime-flash 等，当前配置为 OpenAI 模型名");
        }
    }

    private static String resolveDashScopeVoice(LinkxProperties.LinkMate cfg) {
        String voice = StringUtils.hasText(cfg.getRealtimeVoice()) ? cfg.getRealtimeVoice().trim() : "longanqian";
        if ("marin".equalsIgnoreCase(voice) || "alloy".equalsIgnoreCase(voice)) {
            return "longanqian";
        }
        return voice;
    }

    public static boolean usesWebSocketBridge(String model) {
        if (!StringUtils.hasText(model)) {
            return true;
        }
        String lower = model.trim().toLowerCase();
        return lower.startsWith("qwen-audio") || lower.contains("livetranslate");
    }

    private static String extractMaasHost(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            return StringUtils.hasText(host) ? host : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasWorkspaceSubdomain(String lowerUrl) {
        return lowerUrl.contains(".cn-beijing.maas.aliyuncs.com")
                || lowerUrl.contains(".ap-southeast-1.maas.aliyuncs.com");
    }

    private static String extractWorkspaceId(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String trimmed = baseUrl.trim();
        int schemeEnd = trimmed.indexOf("//");
        if (schemeEnd < 0) {
            return null;
        }
        int hostStart = schemeEnd + 2;
        int hostEnd = trimmed.indexOf('/', hostStart);
        String host = hostEnd > hostStart ? trimmed.substring(hostStart, hostEnd) : trimmed.substring(hostStart);
        String lowerHost = host.toLowerCase();
        if (!lowerHost.contains(".maas.aliyuncs.com")) {
            return null;
        }
        int dot = host.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String workspace = host.substring(0, dot);
        if (!StringUtils.hasText(workspace) || "cn-beijing".equalsIgnoreCase(workspace)
                || "ap-southeast-1".equalsIgnoreCase(workspace)) {
            return null;
        }
        return workspace;
    }

    private String extractDashScopeErrorMessage(int statusCode, String body, String model) {
        if (!StringUtils.hasText(body)) {
            if (statusCode == 404) {
                return "信令地址不存在(404)。qwen-audio 模型 WebRTC 请使用 https://dashscope.aliyuncs.com 或仅填 Workspace 作业务空间；"
                        + "模型请设为 " + model;
            }
            return "上游未返回错误详情(status=" + statusCode + ")";
        }
        return extractErrorMessage(body);
    }

    private static String abbreviateUrl(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) + "?..." : url;
    }

    private static boolean isDashScopeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("aliyuncs.com") || lower.contains("dashscope");
    }

    private static boolean isOpenAiRealtimeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("openai.com") || lower.contains("openai.azure");
    }

    private static boolean isIncompatibleRealtimeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("siliconflow")
                || lower.contains("deepseek")
                || lower.contains("api.deepseek.com");
    }

    private static String normalizeForDetect(String url) {
        return StringUtils.hasText(url) ? url.trim().toLowerCase() : "";
    }

    private static String safetyIdentifier(Long userId) {
        String raw = "linkx-user-" + (userId != null ? userId : "anon") + "-" + UUID.randomUUID();
        return HexFormat.of().formatHex(raw.getBytes(StandardCharsets.UTF_8))
                .substring(0, Math.min(64, raw.length() * 2));
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
