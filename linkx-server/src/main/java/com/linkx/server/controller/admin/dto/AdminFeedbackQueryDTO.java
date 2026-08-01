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
}
