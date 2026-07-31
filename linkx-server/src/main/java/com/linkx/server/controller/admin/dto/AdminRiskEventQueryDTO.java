package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "风险事件列表查询")
public class AdminRiskEventQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "处置状态：pending/handled/ignored")
    private String eventStatus;

    @Schema(description = "事件类型：SENSITIVE_WORD_MATCH/MESSAGE_STORM")
    private String eventType;

    @Schema(description = "风险等级：low/medium/high")
    private String riskLevel;
}
