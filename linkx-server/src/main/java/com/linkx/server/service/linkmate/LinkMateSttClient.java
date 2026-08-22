package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * OpenAI 兼容语音转写客户端（Whisper /audio/transcriptions）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMateSttClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final int STT_MAX_ATTEMPTS = 2;
    private static final long STT_RETRY_DELAY_MS = 800L;

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public record TranscribeResult(String text, String language) {
    }

    public TranscribeResult transcribe(byte[] audioBytes, String filename, String contentType, String languageHint) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new CustomException(400, "语音文件为空");
        }
        if (audioBytes.length > MAX_AUDIO_BYTES) {
            throw new CustomException(400, "语音文件过大，暂不支持转写");
        }
        LinkxProperties.LinkMate cfg = requireConfig();
        String apiKey = resolveSttApiKey(cfg);
        String baseUrl = resolveSttBaseUrl(cfg);
        String model = StringUtils.hasText(cfg.getSttModel()) ? cfg.getSttModel().trim() : "whisper-1";
        String safeName = StringUtils.hasText(filename) ? filename.trim() : "voice.webm";
        String mime = StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
        String boundary = "----LinkX" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, audioBytes, safeName, mime, model, languageHint);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(baseUrl) + "/audio/transcriptions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        for (int attempt = 1; attempt <= STT_MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("LinkMate STT error attempt={} status={} body={}",
                            attempt, response.statusCode(), abbreviate(response.body()));
                    if (attempt < STT_MAX_ATTEMPTS && isTransientHttpStatus(response.statusCode())) {
                        sleepBeforeRetry();
                        continue;
                    }
                    throw new CustomException(502, "语音转写服务暂时不可用，请稍后重试");
                }
                JsonNode root = objectMapper.readTree(response.body());
                String text = root.path("text").asText("").trim();
                if (!StringUtils.hasText(text)) {
                    throw new CustomException(502, "未识别到有效语音内容");
                }
                String language = root.path("language").asText(null);
                return new TranscribeResult(text, language);
            } catch (CustomException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < STT_MAX_ATTEMPTS) {
                    log.warn("LinkMate STT request failed attempt={}", attempt, ex);
                    sleepBeforeRetry();
                    continue;
                }
                log.error("LinkMate STT request failed", ex);
                throw new CustomException(502, "语音转写请求失败");
            }
        }
        throw new CustomException(502, "语音转写请求失败");
    }

    private LinkxProperties.LinkMate requireConfig() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        if (!cfg.isEnabled()) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        String apiKey = resolveSttApiKey(cfg);
        String baseUrl = resolveSttBaseUrl(cfg);
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(503, "语音转写未配置 API Key，请在管理端灵伴 AI 中单独填写");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new CustomException(503, "语音转写未配置 API 地址，请在管理端灵伴 AI 中单独填写");
        }
        return cfg;
    }

    /** 优先使用独立 STT Key，否则回退到灵伴 LLM Key */
    private static String resolveSttApiKey(LinkxProperties.LinkMate cfg) {
        if (StringUtils.hasText(cfg.getSttApiKey())) {
            return cfg.getSttApiKey().trim();
        }
        return StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey().trim() : "";
    }

    /** 优先使用独立 STT 基址，否则回退到灵伴 LLM 基址 */
    private static String resolveSttBaseUrl(LinkxProperties.LinkMate cfg) {
        if (StringUtils.hasText(cfg.getSttBaseUrl())) {
            return cfg.getSttBaseUrl().trim();
        }
        return StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl().trim() : "";
    }

    private static byte[] buildMultipartBody(
            String boundary,
            byte[] fileBytes,
            String filename,
            String contentType,
            String model,
            String languageHint) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "model", null, model.getBytes(StandardCharsets.UTF_8));
        writePart(out, boundary, "response_format", null, "json".getBytes(StandardCharsets.UTF_8));
        writePart(out, boundary, "temperature", null, "0".getBytes(StandardCharsets.UTF_8));
        if (StringUtils.hasText(languageHint)) {
            writePart(out, boundary, "language", null, languageHint.trim().getBytes(StandardCharsets.UTF_8));
        }
        writeFilePart(out, boundary, "file", filename, contentType, fileBytes);
        String end = "--" + boundary + "--\r\n";
        out.writeBytes(end.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String name, String filename, byte[] data) {
        out.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (StringUtils.hasText(filename)) {
            out.writeBytes(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        } else {
            out.writeBytes(("Content-Disposition: form-data; name=\"" + name + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        out.writeBytes(data);
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] data) {
        out.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(data);
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
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
            Thread.sleep(STT_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
