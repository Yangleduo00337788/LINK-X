package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "异常访问概览")
public class AdminAbnormalAccessSummaryVO {

    @Schema(description = "近 24 小时登录失败次数")
    private long loginFail24h;

    @Schema(description = "当前限流命中数")
    private long rateLimitActive;

    @Schema(description = "待处理访问类风险事件数")
    private long riskEventPending;
}
