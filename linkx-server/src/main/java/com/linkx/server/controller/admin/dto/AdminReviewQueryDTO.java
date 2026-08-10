package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审核任务列表查询")
public class AdminReviewQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "审核状态：pending/approved/rejected")
    private String reviewStatus;

    @Schema(description = "来源：report/sensitive/manual")
    private String sourceType;

    @Schema(description = "目标类型：moment/moment_comment/message/announcement 等")
    private String targetType;

    @Schema(description = "风险等级：low/medium/high/critical")
    private String riskLevel;

    @Schema(description = "仅显示超过 SLA 的待审任务")
    private Boolean overdueOnly;

    @Schema(description = "仅显示已督办任务")
    private Boolean escalatedOnly;
}
