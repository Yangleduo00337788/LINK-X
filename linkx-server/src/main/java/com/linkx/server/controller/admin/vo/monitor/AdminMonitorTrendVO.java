package com.linkx.server.controller.admin.vo.monitor;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminMonitorTrendVO {
    private List<String> labels;
    private List<AdminMonitorSeriesVO> series;
}
