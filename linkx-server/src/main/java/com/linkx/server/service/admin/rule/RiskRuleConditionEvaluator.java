package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.entity.admin.SysRiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RiskRuleConditionEvaluator {

    private final ObjectMapper objectMapper;

    public boolean matches(SysRiskRule rule, RiskRuleContext context) {
        if (rule == null || context == null) {
            return false;
        }
        if (StringUtils.hasText(rule.getConditionJson())) {
            return evaluateConditionTree(rule.getConditionJson(), context);
        }
        return matchesLegacy(rule, context);
    }

    static boolean matchesLegacy(SysRiskRule rule, RiskRuleContext context) {
        if (StringUtils.hasText(rule.getKeyword())) {
            String content = context.getText() == null ? "" : context.getText().toLowerCase(Locale.ROOT);
            if (!content.contains(rule.getKeyword().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateConditionTree(String conditionJson, RiskRuleContext context) {
        try {
            JsonNode root = objectMapper.readTree(conditionJson);
            return evaluateNode(root, context);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateNode(JsonNode node, RiskRuleContext context) {
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
                if (evaluateLeafOrGroup(child, context)) {
                    return true;
                }
            }
            return false;
        }
        for (JsonNode child : conditions) {
            if (!evaluateLeafOrGroup(child, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateLeafOrGroup(JsonNode node, RiskRuleContext context) {
        if (node.has("conditions")) {
            return evaluateNode(node, context);
        }
        return evaluateLeaf(node, context);
    }

    private boolean evaluateLeaf(JsonNode node, RiskRuleContext context) {
        String field = node.path("field").asText("");
        String op = node.path("op").asText("eq");
        JsonNode valueNode = node.get("value");
        return switch (field) {
            case "text" -> compareString(context.getText(), op, textValue(valueNode));
            case "historyScore" -> compareNumber(intValue(context.getHistoryScore()), op, intValue(valueNode));
            case "messageCount" -> compareNumber(intValue(context.getMessageCount()), op, intValue(valueNode));
            case "memberCount" -> compareNumber(intValue(context.getMemberCount()), op, intValue(valueNode));
            case "taskRiskLevel" -> compareString(context.getTaskRiskLevel(), op, textValue(valueNode));
            case "sensitiveBlocked" -> compareBoolean(boolValue(context.getSensitiveBlocked()), op, booleanValue(valueNode));
            case "sensitiveAlerted" -> compareBoolean(boolValue(context.getSensitiveAlerted()), op, booleanValue(valueNode));
            case "sensitiveFiltered" -> compareBoolean(boolValue(context.getSensitiveFiltered()), op, booleanValue(valueNode));
            case "escalated" -> compareBoolean(boolValue(isEscalated(context)), op, booleanValue(valueNode));
            default -> false;
        };
    }

    private static boolean isEscalated(RiskRuleContext context) {
        return context.getEscalationCount() != null && context.getEscalationCount() > 0;
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

    private static boolean compareNumber(int actual, String op, int expected) {
        return switch (op) {
            case "eq" -> actual == expected;
            case "neq" -> actual != expected;
            case "gte" -> actual >= expected;
            case "lte" -> actual <= expected;
            case "gt" -> actual > expected;
            case "lt" -> actual < expected;
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

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static int intValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        return node.asInt(0);
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

    private static boolean boolValue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
