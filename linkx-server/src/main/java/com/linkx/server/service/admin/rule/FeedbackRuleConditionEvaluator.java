package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FeedbackRuleConditionEvaluator {

    private final ObjectMapper objectMapper;
    private final LinkxProperties linkxProperties;

    public boolean matches(SysFeedbackDispatchRule rule, Feedback feedback) {
        if (rule == null || feedback == null) {
            return false;
        }
        if (StringUtils.hasText(rule.getConditionJson())) {
            return evaluateConditionTree(rule.getConditionJson(), feedback);
        }
        return matchesLegacy(rule, feedback);
    }

    static boolean matchesLegacy(SysFeedbackDispatchRule rule, Feedback feedback) {
        if (StringUtils.hasText(rule.getFeedbackType())) {
            String expected = rule.getFeedbackType().trim().toLowerCase(Locale.ROOT);
            String actual = feedback.getType() == null ? "" : feedback.getType().trim().toLowerCase(Locale.ROOT);
            if (!expected.equals(actual)) {
                return false;
            }
        }
        if (StringUtils.hasText(rule.getKeyword())) {
            String content = feedback.getContent() == null ? "" : feedback.getContent().toLowerCase(Locale.ROOT);
            if (!content.contains(rule.getKeyword().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateConditionTree(String conditionJson, Feedback feedback) {
        try {
            JsonNode root = objectMapper.readTree(conditionJson);
            return evaluateNode(root, feedback);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateNode(JsonNode node, Feedback feedback) {
        if (node == null || node.isNull()) {
            return true;
        }
        String op = node.path("op").asText("and");
        JsonNode conditions = node.get("conditions");
        if (conditions == null || !conditions.isArray() || conditions.isEmpty()) {
            return true;
        }
        if ("or".equalsIgnoreCase(op)) {
            for (JsonNode child : conditions) {
                if (evaluateLeafOrGroup(child, feedback)) {
                    return true;
                }
            }
            return false;
        }
        for (JsonNode child : conditions) {
            if (!evaluateLeafOrGroup(child, feedback)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateLeafOrGroup(JsonNode node, Feedback feedback) {
        if (node.has("conditions")) {
            return evaluateNode(node, feedback);
        }
        return evaluateLeaf(node, feedback);
    }

    private boolean evaluateLeaf(JsonNode node, Feedback feedback) {
        String field = node.path("field").asText("");
        String op = node.path("op").asText("eq");
        JsonNode valueNode = node.get("value");
        return switch (field) {
            case "type" -> compareString(feedback.getType(), op, textValue(valueNode));
            case "content" -> compareString(feedback.getContent(), op, textValue(valueNode));
            case "status" -> compareString(feedback.getStatus(), op, textValue(valueNode));
            case "overdue" -> compareBoolean(isOverdue(feedback), op, booleanValue(valueNode));
            case "hasAssignee" -> compareBoolean(feedback.getAssigneeId() != null, op, booleanValue(valueNode));
            default -> false;
        };
    }

    private boolean isOverdue(Feedback feedback) {
        if (feedback.getCreateTime() == null) {
            return false;
        }
        if (!"pending".equalsIgnoreCase(feedback.getStatus())) {
            return false;
        }
        int slaHours = resolveSlaHours();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -slaHours);
        return feedback.getCreateTime().before(cal.getTime());
    }

    private int resolveSlaHours() {
        LinkxProperties.App app = linkxProperties.getApp();
        Integer hours = app != null ? app.getFeedbackSlaHours() : null;
        if (hours == null || hours < 1) {
            return 24;
        }
        return Math.min(hours, 720);
    }

    private static boolean compareString(String actual, String op, String expected) {
        String left = actual == null ? "" : actual.trim().toLowerCase(Locale.ROOT);
        String right = expected == null ? "" : expected.trim().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "eq" -> left.equals(right);
            case "neq" -> !left.equals(right);
            case "contains" -> left.contains(right);
            case "not_contains" -> !left.contains(right);
            default -> false;
        };
    }

    private static boolean compareBoolean(boolean actual, String op, boolean expected) {
        return switch (op) {
            case "eq" -> actual == expected;
            case "neq" -> actual != expected;
            default -> false;
        };
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private static boolean booleanValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return "true".equalsIgnoreCase(node.asText());
    }
}
