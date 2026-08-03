package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSeriesVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTrendVO;
import com.linkx.server.service.admin.MonitorSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSnapshotServiceImpl implements MonitorSnapshotService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long RECORD_INTERVAL_MS = 60_000;

    private final JdbcTemplate jdbcTemplate;
    private volatile long lastRecordAt;

    @Override
    public void recordIfDue(String category, Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRecordAt < RECORD_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            if (now - lastRecordAt < RECORD_INTERVAL_MS) {
                return;
            }
            LocalDateTime snapshotAt = LocalDateTime.now(ZONE);
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                try {
                    jdbcTemplate.update(
                            """
                                    INSERT INTO sys_monitor_metric_snapshot
                                    (snapshot_at, category, metric_key, metric_value)
                                    VALUES (?, ?, ?, ?)
                                    """,
                            snapshotAt, category, entry.getKey(), entry.getValue());
                } catch (DataAccessException ex) {
                    log.debug("Monitor snapshot skipped: {}", ex.getMessage());
                    return;
                }
            }
            lastRecordAt = now;
        }
    }

    @Override
    public AdminMonitorTrendVO loadHourlyTrend(String category, String metricKey, int hours) {
        int safeHours = Math.min(168, Math.max(1, hours));
        LocalDateTime since = LocalDateTime.now(ZONE).minusHours(safeHours);
        return loadTrend(category, metricKey, since, "hour", safeHours);
    }

    @Override
    public AdminMonitorTrendVO loadDailyTrend(String category, String metricKey, int days) {
        int safeDays = Math.min(90, Math.max(1, days));
        LocalDateTime since = LocalDateTime.now(ZONE).minusDays(safeDays);
        return loadTrend(category, metricKey, since, "day", safeDays);
    }

    private AdminMonitorTrendVO loadTrend(
            String category, String metricKey, LocalDateTime since, String bucket, int bucketCount) {
        try {
            String sql = "day".equals(bucket)
                    ? """
                    SELECT DATE_FORMAT(snapshot_at, '%m-%d') AS label,
                           AVG(metric_value) AS val
                    FROM sys_monitor_metric_snapshot
                    WHERE category = ? AND metric_key = ? AND snapshot_at >= ?
                    GROUP BY DATE(snapshot_at)
                    ORDER BY DATE(snapshot_at)
                    """
                    : """
                    SELECT DATE_FORMAT(snapshot_at, '%H:%i') AS label,
                           AVG(metric_value) AS val
                    FROM sys_monitor_metric_snapshot
                    WHERE category = ? AND metric_key = ? AND snapshot_at >= ?
                    GROUP BY DATE_FORMAT(snapshot_at, '%Y-%m-%d %H:00')
                    ORDER BY DATE_FORMAT(snapshot_at, '%Y-%m-%d %H:00')
                    """;
            List<String> labels = new ArrayList<>();
            List<Number> data = new ArrayList<>();
            jdbcTemplate.query(sql, rs -> {
                labels.add(rs.getString("label"));
                data.add(rs.getDouble("val"));
            }, category, metricKey, since);
            if (labels.isEmpty()) {
                return emptyTrend(bucketCount);
            }
            return AdminMonitorTrendVO.builder()
                    .labels(labels)
                    .series(List.of(AdminMonitorSeriesVO.builder()
                            .key(metricKey)
                            .name(metricKey)
                            .data(data)
                            .build()))
                    .build();
        } catch (DataAccessException ex) {
            log.debug("Monitor trend unavailable: {}", ex.getMessage());
            return emptyTrend(bucketCount);
        }
    }

    private AdminMonitorTrendVO emptyTrend(int points) {
        List<String> labels = new ArrayList<>();
        List<Number> zeros = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:00");
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (int i = points - 1; i >= 0; i--) {
            labels.add(now.minusHours(i).format(fmt));
            zeros.add(0);
        }
        return AdminMonitorTrendVO.builder()
                .labels(labels)
                .series(List.of(AdminMonitorSeriesVO.builder()
                        .key("value")
                        .name("value")
                        .data(zeros)
                        .build()))
                .build();
    }
}
