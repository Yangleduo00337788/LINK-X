package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSnapshotServiceImpl implements MonitorSnapshotService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long RECORD_INTERVAL_MS = 60_000;
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:00");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, Long> lastRecordAtByCategory = new ConcurrentHashMap<>();

    @Override
    public void recordIfDue(String category, Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRecordAtByCategory.get(category);
        if (last != null && now - last < RECORD_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            last = lastRecordAtByCategory.get(category);
            if (last != null && now - last < RECORD_INTERVAL_MS) {
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
                    log.debug("Monitor snapshot skipped for {}: {}", category, ex.getMessage());
                    return;
                }
            }
            lastRecordAtByCategory.put(category, now);
        }
    }

    @Override
    public AdminMonitorTrendVO loadHourlyTrend(String category, String metricKey, int hours) {
        return loadHourlyTrend(category, metricKey, hours, null);
    }

    @Override
    public AdminMonitorTrendVO loadHourlyTrend(String category, String metricKey, int hours, Double currentValue) {
        int safeHours = Math.min(168, Math.max(1, hours));
        try {
            LocalDateTime now = LocalDateTime.now(ZONE).withMinute(0).withSecond(0).withNano(0);
            List<String> labels = new ArrayList<>();
            List<LocalDateTime> buckets = new ArrayList<>();
            for (int i = safeHours - 1; i >= 0; i--) {
                LocalDateTime bucket = now.minusHours(i);
                buckets.add(bucket);
                labels.add(bucket.format(HOUR_LABEL));
            }
            LocalDateTime since = buckets.get(0);
            Map<LocalDateTime, Double> dbValues = queryHourlyAverages(category, metricKey, since);
            List<Number> data = new ArrayList<>();
            for (LocalDateTime bucket : buckets) {
                Double val = dbValues.get(bucket);
                data.add(val != null ? val : 0);
            }
            overlayCurrentValue(data, currentValue);
            return buildTrend(metricKey, labels, data);
        } catch (DataAccessException ex) {
            log.debug("Monitor hourly trend unavailable: {}", ex.getMessage());
            return fallbackHourlyTrend(metricKey, hours, currentValue);
        }
    }

    @Override
    public AdminMonitorTrendVO loadDailyTrend(String category, String metricKey, int days) {
        int safeDays = Math.min(90, Math.max(1, days));
        try {
            LocalDateTime today = LocalDateTime.now(ZONE).toLocalDate().atStartOfDay();
            List<String> labels = new ArrayList<>();
            List<LocalDateTime> buckets = new ArrayList<>();
            for (int i = safeDays - 1; i >= 0; i--) {
                LocalDateTime bucket = today.minusDays(i);
                buckets.add(bucket);
                labels.add(bucket.format(DAY_LABEL));
            }
            LocalDateTime since = buckets.get(0);
            Map<LocalDateTime, Double> dbValues = queryDailyAverages(category, metricKey, since);
            List<Number> data = new ArrayList<>();
            for (LocalDateTime bucket : buckets) {
                Double val = dbValues.get(bucket);
                data.add(val != null ? val : 0);
            }
            return buildTrend(metricKey, labels, data);
        } catch (DataAccessException ex) {
            log.debug("Monitor daily trend unavailable: {}", ex.getMessage());
            return fallbackDailyTrend(metricKey, safeDays);
        }
    }

    private Map<LocalDateTime, Double> queryHourlyAverages(String category, String metricKey, LocalDateTime since) {
        Map<LocalDateTime, Double> map = new HashMap<>();
        jdbcTemplate.query(
                """
                        SELECT DATE_FORMAT(snapshot_at, '%Y-%m-%d %H:00:00') AS bucket,
                               AVG(metric_value) AS val
                        FROM sys_monitor_metric_snapshot
                        WHERE category = ? AND metric_key = ? AND snapshot_at >= ?
                        GROUP BY DATE_FORMAT(snapshot_at, '%Y-%m-%d %H:00')
                        ORDER BY bucket
                        """,
                rs -> {
                    String bucket = rs.getString("bucket");
                    if (bucket != null) {
                        LocalDateTime at = LocalDateTime.parse(bucket, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        map.put(at, rs.getDouble("val"));
                    }
                },
                category, metricKey, since);
        return map;
    }

    private Map<LocalDateTime, Double> queryDailyAverages(String category, String metricKey, LocalDateTime since) {
        Map<LocalDateTime, Double> map = new HashMap<>();
        jdbcTemplate.query(
                """
                        SELECT DATE(snapshot_at) AS bucket,
                               AVG(metric_value) AS val
                        FROM sys_monitor_metric_snapshot
                        WHERE category = ? AND metric_key = ? AND snapshot_at >= ?
                        GROUP BY DATE(snapshot_at)
                        ORDER BY bucket
                        """,
                rs -> {
                    var date = rs.getDate("bucket");
                    if (date != null) {
                        LocalDateTime at = date.toLocalDate().atStartOfDay();
                        map.put(at, rs.getDouble("val"));
                    }
                },
                category, metricKey, since);
        return map;
    }

    private AdminMonitorTrendVO fallbackHourlyTrend(String metricKey, int hours, Double currentValue) {
        int safeHours = Math.min(168, Math.max(1, hours));
        LocalDateTime now = LocalDateTime.now(ZONE).withMinute(0).withSecond(0).withNano(0);
        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        for (int i = safeHours - 1; i >= 0; i--) {
            labels.add(now.minusHours(i).format(HOUR_LABEL));
            data.add(0);
        }
        overlayCurrentValue(data, currentValue);
        return buildTrend(metricKey, labels, data);
    }

    private AdminMonitorTrendVO fallbackDailyTrend(String metricKey, int days) {
        LocalDateTime today = LocalDateTime.now(ZONE).toLocalDate().atStartOfDay();
        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            labels.add(today.minusDays(i).format(DAY_LABEL));
            data.add(0);
        }
        return buildTrend(metricKey, labels, data);
    }

    private static void overlayCurrentValue(List<Number> data, Double currentValue) {
        if (currentValue == null || data.isEmpty()) {
            return;
        }
        data.set(data.size() - 1, currentValue);
    }

    private static AdminMonitorTrendVO buildTrend(String metricKey, List<String> labels, List<Number> data) {
        return AdminMonitorTrendVO.builder()
                .labels(labels)
                .series(List.of(AdminMonitorSeriesVO.builder()
                        .key(metricKey)
                        .name(metricKey)
                        .data(data)
                        .build()))
                .build();
    }
}
