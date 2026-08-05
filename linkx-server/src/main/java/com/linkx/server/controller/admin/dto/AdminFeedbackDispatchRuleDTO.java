package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "反馈分流规则")
public class AdminFeedbackDispatchRuleDTO {

    @NotBlank
    @Schema(description = "规则名称")
    private String name;

    @Schema(description = "反馈类型匹配，空表示任意（兼容旧规则）")
    private String feedbackType;

    @Schema(description = "内容关键词包含匹配，空表示不限制（兼容旧规则）")
    private String keyword;

    @Schema(description = "扩展条件树 JSON")
    private String conditionJson;

    @Schema(description = "固定处理人（assigneeSource=fixed）")
    private Long assigneeId;

    @Schema(description = "处理人来源：fixed|duty|round_robin")
    private String assigneeSource;

    @Schema(description = "值班表 ID（assigneeSource=duty）")
    private Long dutyScheduleId;

    @Schema(description = "轮询池等动作配置 JSON")
    private String actionConfig;

    @Schema(description = "动作类型：assign|notify|assign_notify")
    private String actionType;

    @Schema(description = "通知角色编码，逗号分隔")
    private String notifyRoles;

    @Schema(description = "通知渠道，逗号分隔：sse,email")
    private String notifyChannels;

    @Schema(description = "优先级，越大越先匹配")
    private Integer priority;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
