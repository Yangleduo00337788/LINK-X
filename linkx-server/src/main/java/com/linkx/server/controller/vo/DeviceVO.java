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
    private Date lastActive;
    private boolean current;
}
