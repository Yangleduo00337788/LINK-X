package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 API 客户端（DeepSeek / 通义 / OpenAI 等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateLlmClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public record LlmMessage(String role, String content) {
    }

    public record LlmResult(String content, int totalTokens) {
    }

    public record StreamDeltaHandlers(
            Consumer<String> onReasoningDelta,
            Consumer<String> onContentDelta) {
    }

    public LlmResult chat(List<LlmMessage> messages) {
        return chat(messages, false);
    }

    public LlmResult chat(List<LlmMessage> messages, boolean deepThinking) {
        LinkxProperties.LinkMate cfg = requireConfig();
        ObjectNode body = buildRequestBody(cfg, messages, false, deepThinking);
        HttpRequest request = buildHttpRequest(cfg, body);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LinkMate LLM error status={} body={}", response.statusCode(), abbreviate(response.body()));
                throw new CustomException(502, "AI 服务暂时不可用，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = extractContent(root);
            int tokens = root.path("usage").path("total_tokens").asInt(estimateTokens(messages, content));
            return new LlmResult(content, tokens);
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LinkMate LLM request failed", ex);
            throw new CustomException(502, "AI 服务请求失败");
        }
    }

  /**
   * 流式对话；返回估算 token 总量。
   */
    public int streamChat(List<LlmMessage> messages, Consumer<String> onDelta) {
        return streamChat(messages, false, new StreamDeltaHandlers(null, onDelta));
    }

    public int streamChat(List<LlmMessage> messages, boolean deepThinking, StreamDeltaHandlers handlers) {
        LinkxProperties.LinkMate cfg = requireConfig();
        ObjectNode body = buildRequestBody(cfg, messages, true, deepThinking);
        HttpRequest request = buildHttpRequest(cfg, body);

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String err = readAll(response.body());
                log.warn("LinkMate LLM stream error status={} body={}", response.statusCode(), abbreviate(err));
                throw new CustomException(502, "AI 服务暂时不可用，请稍后重试");
            }

            StringBuilder reasoning = new StringBuilder();
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode delta = root.path("choices").path(0).path("delta");
                    String reasoningChunk = delta.path("reasoning_content").asText(null);
                    String contentChunk = delta.path("content").asText(null);
                    if (StringUtils.hasText(reasoningChunk)) {
                        reasoning.append(reasoningChunk);
                        if (handlers.onReasoningDelta() != null) {
                            handlers.onReasoningDelta().accept(reasoningChunk);
                        }
                    }
                    if (StringUtils.hasText(contentChunk)) {
                        content.append(contentChunk);
                        if (handlers.onContentDelta() != null) {
                            handlers.onContentDelta().accept(contentChunk);
                        }
                    }
                }
            }
            String full = content.length() > 0 ? content.toString() : reasoning.toString();
            if (!StringUtils.hasText(full)) {
                throw new CustomException(502, "AI 未返回有效内容");
            }
            return estimateTokens(messages, full);
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LinkMate LLM stream failed", ex);
            throw new CustomException(502, "AI 服务请求失败");
        }
    }

    private LinkxProperties.LinkMate requireConfig() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled()) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new CustomException(503, "灵伴服务未配置 API Key");
        }
        if (!StringUtils.hasText(cfg.getBaseUrl())) {
            throw new CustomException(503, "灵伴服务未配置 API 地址");
        }
        return cfg;
    }

    private ObjectNode buildRequestBody(
            LinkxProperties.LinkMate cfg,
            List<LlmMessage> messages,
            boolean stream,
            boolean deepThinking) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", LinkMateModelCapability.resolveModel(cfg.getModel(), deepThinking));
        body.put("temperature", cfg.getTemperature());
        body.put("max_tokens", cfg.getMaxTokens());
        body.put("stream", stream);

        ArrayNode msgArray = body.putArray("messages");
        for (LlmMessage msg : messages) {
            ObjectNode node = msgArray.addObject();
            node.put("role", msg.role());
            node.put("content", msg.content());
        }
        return body;
    }

    private HttpRequest buildHttpRequest(LinkxProperties.LinkMate cfg, ObjectNode body) {
        String url = normalizeBaseUrl(cfg.getBaseUrl()) + "/chat/completions";
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (Exception ex) {
            throw new CustomException(500, "构建 AI 请求失败");
        }
    }

    private String extractContent(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
            throw new CustomException(502, "AI 未返回有效内容");
        }
        return content.asText();
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

    private static int estimateTokens(List<LlmMessage> messages, String output) {
        int chars = output.length();
        for (LlmMessage msg : messages) {
            chars += msg.content().length();
        }
        return Math.max(1, chars / 3);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private static String readAll(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
