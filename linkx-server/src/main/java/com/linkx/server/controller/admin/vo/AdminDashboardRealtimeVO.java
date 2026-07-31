package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘实时指标")
public class AdminDashboardRealtimeVO {

    @Schema(description = "近 15 分钟活跃设备数")
    private long onlineDevices;

    @Schema(description = "今日新增用户")
    private long todayNewUsers;

    @Schema(description = "今日消息量")
    private long todayMessages;

    @Schema(description = "今日登录成功次数")
    private long todayLogins;

    @Schema(description = "近 24 小时风险事件")
    private long riskEvents24h;
}
