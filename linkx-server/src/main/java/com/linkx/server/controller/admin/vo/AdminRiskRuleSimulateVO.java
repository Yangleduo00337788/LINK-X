package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "风控规则模拟结果")
public class AdminRiskRuleSimulateVO {

    private int scoreDelta;
    private boolean blocked;
    private boolean alerted;
    private List<String> factors;
    private List<MatchedRuleVO> matchedRules;

    @Data
    @Builder
    public static class MatchedRuleVO {
        private Long ruleId;
        private String ruleName;
        private Integer scoreDelta;
        private String actionType;
    }
}
