package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "管理端设备会话")
public class AdminDeviceVO {

    @Schema(description = "会话主键")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "登录 IP")
    private String ip;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "最近活跃时间")
    private Date lastActive;

    @Schema(description = "首次登记时间")
    private Date createTime;

    @Schema(description = "当前是否在线（WebSocket 连接存在）")
    private Boolean online;

    @Schema(description = "该用户下此设备是否处于长期封禁")
    private Boolean banned;
}
