package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "反馈分流规则")
public class AdminFeedbackDispatchRuleDTO {

    @NotBlank
    @Schema(description = "规则名称")
    private String name;

    @Schema(description = "反馈类型匹配，空表示任意")
    private String feedbackType;

    @Schema(description = "内容关键词包含匹配，空表示不限制")
    private String keyword;

    @NotNull
    @Schema(description = "指派处理人")
    private Long assigneeId;

    @Schema(description = "优先级，越大越先匹配")
    private Integer priority;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
