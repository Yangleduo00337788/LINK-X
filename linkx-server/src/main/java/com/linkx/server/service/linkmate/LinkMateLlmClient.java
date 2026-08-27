package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import java.util.function.BooleanSupplier;
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
    private static final int LLM_MAX_ATTEMPTS = 2;
    private static final long LLM_RETRY_DELAY_MS = 800L;

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public record LlmMessage(String role, String content) {
    }

    public record LlmResult(String content, int totalTokens) {
    }

    public record ToolCall(String id, String name, String argumentsJson) {
    }

    public record StreamResult(
            String content,
            String reasoning,
            int totalTokens,
            boolean cancelled,
            List<ToolCall> toolCalls) {

        public StreamResult(String content, String reasoning, int totalTokens, boolean cancelled) {
            this(content, reasoning, totalTokens, cancelled, List.of());
        }
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

        for (int attempt = 1; attempt <= LLM_MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("LinkMate LLM error attempt={} status={} body={}",
                            attempt, response.statusCode(), abbreviate(response.body()));
                    if (attempt < LLM_MAX_ATTEMPTS && isTransientHttpStatus(response.statusCode())) {
                        sleepBeforeRetry();
                        continue;
                    }
                    throw new CustomException(502, "AI 服务暂时不可用，请稍后重试");
                }
                JsonNode root = objectMapper.readTree(response.body());
                String content = extractContent(root);
                int tokens = extractTotalTokens(root, estimateTokens(messages, content));
                return new LlmResult(content, tokens);
            } catch (CustomException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < LLM_MAX_ATTEMPTS) {
                    log.warn("LinkMate LLM request failed attempt={}", attempt, ex);
                    sleepBeforeRetry();
                    continue;
                }
                log.error("LinkMate LLM request failed", ex);
                throw new CustomException(502, "AI 服务请求失败");
            }
        }
        throw new CustomException(502, "AI 服务请求失败");
    }

  /**
   * 流式对话；优先使用 SSE 中的 usage.total_tokens，否则估算。
   */
    public StreamResult streamChat(
            List<LlmMessage> messages,
            boolean deepThinking,
            StreamDeltaHandlers handlers,
            BooleanSupplier cancelled) {
        return streamChat(messages, deepThinking, handlers, cancelled, null);
    }

    public StreamResult streamChat(
            List<LlmMessage> messages,
            boolean deepThinking,
            StreamDeltaHandlers handlers,
            BooleanSupplier cancelled,
            ArrayNode tools) {
        LinkxProperties.LinkMate cfg = requireConfig();
        ObjectNode body = buildRequestBody(cfg, messages, true, deepThinking, tools);
        HttpRequest request = buildHttpRequest(cfg, body);

        for (int attempt = 1; attempt <= LLM_MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String err = readAll(response.body());
                    log.warn("LinkMate LLM stream error attempt={} status={} body={}",
                            attempt, response.statusCode(), abbreviate(err));
                    if (attempt < LLM_MAX_ATTEMPTS && isTransientHttpStatus(response.statusCode())) {
                        sleepBeforeRetry();
                        continue;
                    }
                    throw new CustomException(502, "AI 服务暂时不可用，请稍后重试");
                }

                return readStreamResponse(response.body(), messages, handlers, cancelled);
            } catch (CustomException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < LLM_MAX_ATTEMPTS) {
                    log.warn("LinkMate LLM stream failed attempt={}", attempt, ex);
                    sleepBeforeRetry();
                    continue;
                }
                log.error("LinkMate LLM stream failed", ex);
                throw new CustomException(502, "AI 服务请求失败");
            }
        }
        throw new CustomException(502, "AI 服务请求失败");
    }

    private StreamResult readStreamResponse(
            InputStream bodyStream,
            List<LlmMessage> messages,
            StreamDeltaHandlers handlers,
            BooleanSupplier cancelled) throws Exception {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int totalTokens = 0;
        boolean usageReported = false;
        Map<Integer, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();

        try (InputStream stream = bodyStream;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    stream.close();
                    break;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                int reported = extractTotalTokens(root, 0);
                if (reported > 0) {
                    totalTokens = reported;
                    usageReported = true;
                }
                JsonNode delta = root.path("choices").path(0).path("delta");
                String reasoningChunk = delta.path("reasoning_content").asText(null);
                String contentChunk = delta.path("content").asText(null);
                if (delta.has("tool_calls")) {
                    accumulateToolCalls(delta.path("tool_calls"), toolCallBuilders);
                }
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

        List<ToolCall> toolCalls = finalizeToolCalls(toolCallBuilders);
        boolean wasCancelled = cancelled != null && cancelled.getAsBoolean();
        if (wasCancelled) {
            String partial = content.length() > 0 ? content.toString() : reasoning.toString();
            int tokens = usageReported ? totalTokens : estimateTokens(messages, partial);
            return new StreamResult(partial, reasoning.toString(), tokens, true, toolCalls);
        }

        String full = content.toString();
        if (!StringUtils.hasText(full) && toolCalls.isEmpty()) {
            throw new CustomException(502, "AI 未返回有效内容");
        }
        int tokens = usageReported ? totalTokens : estimateTokens(messages, full);
        return new StreamResult(full, reasoning.toString(), tokens, false, toolCalls);
    }

    /** 兼容旧调用 */
    public int streamChat(List<LlmMessage> messages, Consumer<String> onDelta) {
        return streamChat(messages, false, new StreamDeltaHandlers(null, onDelta), null).totalTokens();
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
        return buildRequestBody(cfg, messages, stream, deepThinking, null);
    }

    private ObjectNode buildRequestBody(
            LinkxProperties.LinkMate cfg,
            List<LlmMessage> messages,
            boolean stream,
            boolean deepThinking,
            ArrayNode tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", LinkMateModelCapability.resolveModel(cfg.getModel(), deepThinking));
        body.put("temperature", cfg.getTemperature());
        body.put("max_tokens", cfg.getMaxTokens());
        body.put("stream", stream);
        if (stream) {
            ObjectNode streamOptions = body.putObject("stream_options");
            streamOptions.put("include_usage", true);
        }
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }

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

    private static int extractTotalTokens(JsonNode root, int fallback) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode()) {
            return fallback;
        }
        int total = usage.path("total_tokens").asInt(0);
        return total > 0 ? total : fallback;
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

    private static boolean isTransientHttpStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(LLM_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    private static void accumulateToolCalls(JsonNode toolCallsNode, Map<Integer, ToolCallBuilder> builders) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return;
        }
        for (JsonNode item : toolCallsNode) {
            int index = item.path("index").asInt(builders.size());
            ToolCallBuilder builder = builders.computeIfAbsent(index, ignored -> new ToolCallBuilder());
            String id = item.path("id").asText(null);
            if (StringUtils.hasText(id)) {
                builder.id = id;
            }
            JsonNode fn = item.path("function");
            String name = fn.path("name").asText(null);
            if (StringUtils.hasText(name)) {
                builder.name = name;
            }
            String argsChunk = fn.path("arguments").asText(null);
            if (StringUtils.hasText(argsChunk)) {
                builder.arguments.append(argsChunk);
            }
        }
    }

    private static List<ToolCall> finalizeToolCalls(Map<Integer, ToolCallBuilder> builders) {
        if (builders.isEmpty()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallBuilder builder : builders.values()) {
            if (!StringUtils.hasText(builder.name)) {
                continue;
            }
            String id = StringUtils.hasText(builder.id) ? builder.id : "call_" + calls.size();
            calls.add(new ToolCall(id, builder.name, builder.arguments.toString()));
        }
        return calls;
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
