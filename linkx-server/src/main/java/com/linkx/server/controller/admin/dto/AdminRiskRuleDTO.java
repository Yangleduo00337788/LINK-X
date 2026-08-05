package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "风控自定义规则")
public class AdminRiskRuleDTO {

    @NotBlank
    @Schema(description = "规则名称")
    private String name;

    @Schema(description = "作用域：global|review|message|simulate")
    private String scope;

    @Schema(description = "关键词包含匹配（简配）")
    private String keyword;

    @Schema(description = "条件树 JSON")
    private String conditionJson;

    @Schema(description = "命中加分")
    private Integer scoreDelta;

    @Schema(description = "动作：score_only|block|alert|escalate")
    private String actionType;

    @Schema(description = "动作扩展配置 JSON")
    private String actionConfig;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
