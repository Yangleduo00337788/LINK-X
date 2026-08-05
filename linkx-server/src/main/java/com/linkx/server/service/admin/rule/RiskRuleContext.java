package com.linkx.server.service.admin.rule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskRuleContext {

    private String scope;
    private String text;
    private Long subjectUserId;
    private Integer historyScore;
    private Integer messageCount;
    private Integer memberCount;
    private String taskRiskLevel;
    private Boolean sensitiveBlocked;
    private Boolean sensitiveAlerted;
    private Boolean sensitiveFiltered;
    private Integer escalationCount;
}
