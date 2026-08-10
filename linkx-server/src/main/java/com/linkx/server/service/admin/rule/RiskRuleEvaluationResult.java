package com.linkx.server.service.admin.rule;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RiskRuleEvaluationResult {

    @Builder.Default
    private int scoreDelta = 0;

    @Builder.Default
    private List<String> factors = new ArrayList<>();

    @Builder.Default
    private List<MatchedRule> matchedRules = new ArrayList<>();

    @Builder.Default
    private boolean blocked = false;

    @Builder.Default
    private boolean alerted = false;

    @Data
    @Builder
    public static class MatchedRule {
        private Long ruleId;
        private String ruleName;
        private Integer scoreDelta;
        private String actionType;
    }
}
