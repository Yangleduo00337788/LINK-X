package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "风控策略命中模拟")
public class AdminRiskPolicySimulateDTO {

    @Schema(description = "待检测文本")
    @Size(max = 2000)
    private String text;

    @Schema(description = "模拟涉事用户 ID（用于叠加历史风险分）")
    private Long subjectUserId;

    @Schema(description = "模拟消息条数（规则链）")
    private Integer messageCount;

    @Schema(description = "模拟群成员数（规则链）")
    private Integer memberCount;
}
