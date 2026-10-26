package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 内容机审：调用 OpenAI 兼容 LLM 打 0–100 分，超过风控里配置的人审上限后入待审队列。
 * 不拦截用户发布，避免把发送链路绑在模型延迟上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContentReviewService {

    private static final String SYSTEM_PROMPT = """
            你是即时通讯/社区内容安全审核模型。根据中国大陆互联网内容规范，给文本打违规风险分。
            关注：色情低俗、暴力、政治敏感、诈骗广告、辱骂仇恨、违禁品等。
            只返回 JSON，不要 Markdown，不要解释。格式：
            {"score":0,"level":"low","labels":[],"reason":""}
            score 为 0 到 100 的整数；level 取 low/medium/high/critical；reason 用中文简述。
            """;

    private final LinkxProperties linkxProperties;
    private final LinkMateLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ReviewRiskScoringService reviewRiskScoringService;
    private final ObjectProvider<AdminReviewService> adminReviewService;
    private final ObjectProvider<AdminRiskEventService> adminRiskEventService;

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ai-review-", 0).factory());

    public record ReviewRequest(
            Long userId,
            String targetType,
            String targetId,
            Long conversationId,
            String content,
            String scene) {
    }

    public record ReviewScore(
            int score,
            String level,
            String reason,
            List<String> labels,
            boolean queuedForHuman) {
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(linkxProperties.getRiskPolicy().getAiReviewEnabled());
    }

    public int humanScoreMin() {
        return linkxProperties.getRiskPolicy().getAiReviewHumanScoreMin();
    }

    /** 发布成功后异步机审；无文本或未开启时直接跳过。 */
    public void submitAfterCommit(ReviewRequest request) {
        if (request == null || !isEnabled() || !StringUtils.hasText(request.content())) {
            return;
        }
        Runnable task = () -> {
            try {
                applyReview(request, review(request.content()));
            } catch (Exception ex) {
                log.warn("AI 机审失败 scene={} target={}:{} : {}",
                        request.scene(), request.targetType(), request.targetId(), ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeAsync(task);
                }
            });
        } else {
            executeAsync(task);
        }
    }

    public ReviewScore review(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) {
            return new ReviewScore(0, "low", "空文本", List.of(), false);
        }
        if (!llmClient.isLlmConfigured()) {
            log.warn("AI 机审已开启但未配置灵伴 API Key/地址，跳过");
            return new ReviewScore(0, "low", "模型未配置", List.of(), false);
        }
        String hints = buildSensitiveHints(text);
        String userPrompt = "场景文本：\n" + abbreviate(text, 2000);
        if (StringUtils.hasText(hints)) {
            userPrompt += "\n词库命中提示（仅供参考）：" + hints;
        }
        LinkMateLlmClient.LlmResult llm = llmClient.chatForReview(List.of(
                new LlmMessage("system", SYSTEM_PROMPT),
                new LlmMessage("user", userPrompt)
        ));
        ReviewScore parsed = parseScore(llm.content());
        boolean queue = parsed.score() >= humanScoreMin();
        return new ReviewScore(parsed.score(), parsed.level(), parsed.reason(), parsed.labels(), queue);
    }

    private void applyReview(ReviewRequest request, ReviewScore scored) {
        AdminReviewService reviewService = adminReviewService.getIfAvailable();
        if (reviewService != null) {
            reviewService.createFromAiReview(
                    request.userId(),
                    request.targetType(),
                    request.targetId(),
                    request.conversationId(),
                    request.content(),
                    scored.score(),
                    scored.level(),
                    scored.reason(),
                    scored.labels());
        }
        if (!scored.queuedForHuman()) {
            return;
        }
        AdminRiskEventService riskEvents = adminRiskEventService.getIfAvailable();
        if (riskEvents != null) {
            riskEvents.recordAiContentReview(
                    request.userId(),
                    scored.score(),
                    scored.level(),
                    scored.reason(),
                    request.conversationId(),
                    request.targetType());
        }
    }

    private void executeAsync(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            log.warn("AI 机审线程池拒绝任务: {}", ex.getMessage());
        }
    }

    private String buildSensitiveHints(String text) {
        if (Boolean.FALSE.equals(linkxProperties.getApp().getSensitiveFilterEnabled())) {
            return "";
        }
        SensitiveWordService.DetectionResult detected = sensitiveWordService.detect(text);
        if (detected == null || detected.matches() == null || detected.matches().isEmpty()) {
            return "";
        }
        return String.join(",", detected.matches());
    }

    ReviewScore parseScore(String raw) {
        JsonNode node = readJsonObject(raw);
        int score = clampScore(node.path("score").asInt(0));
        String level = normalizeLevel(node.path("level").asText(""), score);
        String reason = node.path("reason").asText("");
        if (!StringUtils.hasText(reason)) {
            reason = "AI 未给出理由";
        }
        List<String> labels = new java.util.ArrayList<>();
        JsonNode labelsNode = node.path("labels");
        if (labelsNode.isArray()) {
            labelsNode.forEach(item -> {
                if (item != null && StringUtils.hasText(item.asText())) {
                    labels.add(item.asText().trim());
                }
            });
        }
        return new ReviewScore(score, level, reason, List.copyOf(labels), false);
    }

    private JsonNode readJsonObject(String raw) {
        String body = raw == null ? "" : raw.trim();
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start >= 0 && end > start) {
            body = body.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            log.warn("AI 机审返回无法解析: {}", abbreviate(raw, 240));
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeLevel(String raw, int score) {
        String fromModel = raw == null ? "" : raw.trim().toLowerCase();
        if ("low".equals(fromModel) || "medium".equals(fromModel)
                || "high".equals(fromModel) || "critical".equals(fromModel)) {
            return fromModel;
        }
        return reviewRiskScoringService.scoreToLevel(score);
    }

    private static int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "...";
    }

}
