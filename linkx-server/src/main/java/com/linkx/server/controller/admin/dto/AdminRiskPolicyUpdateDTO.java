package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "风控策略更新")
public class AdminRiskPolicyUpdateDTO {

    @Min(1)
  private Integer messageStormUserThreshold;

    @Min(1)
    private Integer messageStormUserWindowSeconds;

    @Min(1)
    private Integer messageStormGroupMinMembers;

    @Min(1)
    private Integer messageStormGroupLargeMembers;

    @Min(1)
    private Integer messageStormGroupMidPerMinute;

    @Min(1)
    private Integer messageStormGroupLargePerMinute;

    @Min(0)
    @Max(100)
    private Integer scoreMediumMin;

    @Min(0)
    @Max(100)
    private Integer scoreHighMin;

    @Min(0)
    @Max(100)
    private Integer scoreCriticalMin;

    @Min(1)
    private Integer rateLimitLoginPerMinute;

    @Min(1)
    private Integer rateLimitRegisterPerMinute;

    @Min(1)
    private Integer rateLimitSearchPerMinute;

    @Min(1)
    private Integer rateLimitListPerMinute;

    @Min(1)
    private Integer rateLimitWritePerMinute;

    @Min(1)
    private Integer rateLimitUploadPerMinute;
}
