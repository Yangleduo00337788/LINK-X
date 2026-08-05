package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "反馈分流规则模拟")
public class AdminFeedbackDispatchSimulateDTO {

    @Schema(description = "反馈类型")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "反馈状态")
    private String status;

    @Schema(description = "是否已有处理人")
    private Boolean hasAssignee;

    @Schema(description = "创建时间偏移小时（用于模拟超时，负数表示更早）")
    private Integer createOffsetHours;
}
