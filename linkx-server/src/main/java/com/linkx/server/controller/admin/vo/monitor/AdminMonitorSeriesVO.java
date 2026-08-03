package com.linkx.server.controller.admin.vo.monitor;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminMonitorSeriesVO {
    private String key;
    private String name;
    private List<Number> data;
}
