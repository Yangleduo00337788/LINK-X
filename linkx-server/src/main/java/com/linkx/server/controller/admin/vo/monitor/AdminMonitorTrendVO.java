package com.linkx.server.controller.admin.vo.monitor;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminMonitorTrendVO {
    private List<String> labels;
    private List<AdminMonitorSeriesVO> series;
}
