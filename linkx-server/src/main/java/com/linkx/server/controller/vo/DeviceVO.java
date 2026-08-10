package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String deviceName;
    private String deviceType;
    private String ip;
    /** 脱敏后的设备浏览器/客户端标识，仅用于展示 */
    private String userAgent;
    private Date lastActive;
    private boolean current;
    /** 管理端：该设备当前是否有 WebSocket 连接 */
    private boolean online;

    /** 管理端：是否处于长期封禁 */
    private boolean banned;

    /** 管理端：是否已批准（强绑定白名单） */
    private boolean approved;
}
