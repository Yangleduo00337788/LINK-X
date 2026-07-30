package com.linkx.server.controller.vo;

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
}
