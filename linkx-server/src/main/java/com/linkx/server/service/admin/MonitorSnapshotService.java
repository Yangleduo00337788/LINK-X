package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTrendVO;

import java.util.Map;

public interface MonitorSnapshotService {

    void recordIfDue(String category, Map<String, Double> metrics);

    AdminMonitorTrendVO loadHourlyTrend(String category, String metricKey, int hours);

    AdminMonitorTrendVO loadHourlyTrend(String category, String metricKey, int hours, Double currentValue);

    AdminMonitorTrendVO loadDailyTrend(String category, String metricKey, int days);
}
