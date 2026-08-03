package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "反馈列表查询")
public class AdminFeedbackQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "反馈状态：pending/replied/closed")
    private String feedbackStatus;

    @Schema(description = "仅看超过 SLA 的待处理反馈")
    private Boolean overdueOnly;

    @Schema(description = "处理人用户ID")
    private Long assigneeId;

    @Schema(description = "仅未指派")
    private Boolean unassignedOnly;

    @Schema(description = "仅指派给我（服务端会覆盖 assigneeId）")
    private Boolean mineOnly;

    @Schema(description = "仅已升级工单")
    private Boolean escalatedOnly;
}
