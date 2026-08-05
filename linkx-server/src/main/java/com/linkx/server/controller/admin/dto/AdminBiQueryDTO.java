package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "BI 高级查询")
public class AdminBiQueryDTO {

    @NotBlank
    @Schema(description = "指标：new_users|logins|messages|feedback|risk_events|reviews")
    private String metric;

    @Schema(description = "维度：none|feedback_type|risk_level|feedback_status")
    private String dimension;

    @Schema(description = "天数 7-90")
    private Integer days;

    @Schema(description = "是否与上一周期对比")
    private Boolean comparePrevious;
}
