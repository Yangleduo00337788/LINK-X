package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "风控规则模拟")
public class AdminRiskRuleSimulateDTO {

    @Schema(description = "作用域")
    private String scope;

    @Schema(description = "文本")
    private String text;

    @Schema(description = "涉事用户 ID")
    private Long subjectUserId;

    @Schema(description = "消息条数")
    private Integer messageCount;

    @Schema(description = "群成员数")
    private Integer memberCount;

    @Schema(description = "任务风险等级")
    private String taskRiskLevel;

    @Schema(description = "敏感词拦截")
    private Boolean sensitiveBlocked;

    @Schema(description = "敏感词告警")
    private Boolean sensitiveAlerted;

    @Schema(description = "敏感词替换")
    private Boolean sensitiveFiltered;

    @Schema(description = "督办次数")
    private Integer escalationCount;
}
