package com.linkx.server.controller.admin.vo.monitor;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminMonitorNamedValueVO {
    private String key;
    private String name;
    private long value;
}
