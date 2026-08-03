package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.vo.monitor.AdminMonitorApiStatsVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorCacheVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorServiceVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSqlVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTaskStatsVO;

public interface AdminSystemMonitorMetricsService {

    AdminMonitorCacheVO cache(int hours);

    AdminMonitorServiceVO service(int hours);

    AdminMonitorApiStatsVO apiStats(int days);

    AdminMonitorTaskStatsVO taskStats(int days);

    AdminMonitorSqlVO sql(int hours, int limit);
}
