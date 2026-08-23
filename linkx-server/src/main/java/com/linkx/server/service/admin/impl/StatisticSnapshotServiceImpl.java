package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.entity.admin.SysAdminDashboardSnapshot;
import com.linkx.server.entity.admin.SysAdminStatisticSnapshot;
import com.linkx.server.mapper.admin.SysAdminDashboardSnapshotMapper;
import com.linkx.server.mapper.admin.SysAdminStatisticSnapshotMapper;
import com.linkx.server.service.admin.AdminDashboardService;
import com.linkx.server.service.admin.StatisticSnapshotService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticSnapshotServiceImpl implements StatisticSnapshotService {

    private static final int MAX_BACKFILL_DAYS = 90;

    private static final String DOMAIN_STATISTIC = "statistic";
    private static final String DOMAIN_HEATMAP = "heatmap";
    private static final List<String> METRICS = List.of(
            "new_users", "logins", "messages", "feedback", "risk_events", "reviews",
            "distinct_logins", "active_devices", "total_users_eod", "group_messages");
    private static final List<String> HEATMAP_METRICS = List.of("messages", "logins");
    private static final String MESSAGE_HEATMAP_SQL = """
            SELECT MOD(DAYOFWEEK(create_time) + 5, 7) AS wd, HOUR(create_time) AS h, COUNT(*) AS c
            FROM im_message
            WHERE deleted = 0 AND create_time >= ? AND create_time < ?
            GROUP BY wd, h
            """;
    private static final String LOGIN_HEATMAP_SQL = """
            SELECT MOD(DAYOFWEEK(create_time) + 5, 7) AS wd, HOUR(create_time) AS h, COUNT(*) AS c
            FROM sys_login_audit
            WHERE success = 1 AND create_time >= ? AND create_time < ?
            GROUP BY wd, h
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SysAdminStatisticSnapshotMapper statisticSnapshotMapper;
    private final SysAdminDashboardSnapshotMapper dashboardSnapshotMapper;
    private final AdminDashboardService adminDashboardService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public int captureYesterdaySnapshots() {
        return captureDaySnapshots(LocalDate.now().minusDays(1), true);
    }

    @Override
    @Transactional
    public int backfillSnapshots(int days) {
        int capped = Math.max(1, Math.min(MAX_BACKFILL_DAYS, days));
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(capped - 1L);
        int total = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            total += captureDaySnapshots(date, false);
        }
        log.info("Backfilled statistic snapshots: days={}, rows={}", capped, total);
        return total;
    }

    private int captureDaySnapshots(LocalDate snapshotDate, boolean includeDashboard) {
        java.util.Date dayStart = java.util.Date.from(
                snapshotDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.Date dayEnd = java.util.Date.from(
                snapshotDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        purgeExisting(snapshotDate, includeDashboard);

        int count = 0;
        for (String metric : METRICS) {
            long value = countMetric(metric, dayStart, dayEnd);
            statisticSnapshotMapper.insert(SysAdminStatisticSnapshot.builder()
                    .snapshotDate(Date.valueOf(snapshotDate))
                    .metricDomain(DOMAIN_STATISTIC)
                    .metricKey(metric)
                    .dimensionKey("all")
                    .dimensionValue(null)
                    .metricValue(value)
                    .build());
            count++;
        }

        for (String heatmapMetric : HEATMAP_METRICS) {
            count += captureHeatmapBuckets(snapshotDate, dayStart, dayEnd, heatmapMetric);
        }

        if (includeDashboard) {
            dashboardSnapshotMapper.insert(SysAdminDashboardSnapshot.builder()
                    .snapshotDate(Date.valueOf(snapshotDate))
                    .summaryJson(serializeSummary(adminDashboardService.summary(null)))
                    .build());
            count++;
        }
        return count;
    }

    private void purgeExisting(LocalDate snapshotDate, boolean includeDashboard) {
        Date sqlDate = Date.valueOf(snapshotDate);
        statisticSnapshotMapper.deleteByQuery(QueryWrapper.create()
                .where(SysAdminStatisticSnapshot::getSnapshotDate).eq(sqlDate));
        if (includeDashboard) {
            dashboardSnapshotMapper.deleteByQuery(QueryWrapper.create()
                    .where(SysAdminDashboardSnapshot::getSnapshotDate).eq(sqlDate));
        }
    }

    private long countMetric(String metric, java.util.Date dayStart, java.util.Date dayEnd) {
        String sql = switch (metric) {
            case "new_users" -> "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND create_time >= ? AND create_time < ?";
            case "logins" -> "SELECT COUNT(*) FROM sys_login_audit WHERE success = 1 AND create_time >= ? AND create_time < ?";
            case "messages" -> "SELECT COUNT(*) FROM im_message WHERE deleted = 0 AND create_time >= ? AND create_time < ?";
            case "feedback" -> "SELECT COUNT(*) FROM sys_feedback WHERE create_time >= ? AND create_time < ?";
            case "risk_events" -> "SELECT COUNT(*) FROM sys_risk_event WHERE create_time >= ? AND create_time < ?";
            case "reviews" -> "SELECT COUNT(*) FROM sys_review_task WHERE create_time >= ? AND create_time < ?";
            case "distinct_logins" ->
                    "SELECT COUNT(DISTINCT user_id) FROM sys_login_audit WHERE success = 1 AND create_time >= ? AND create_time < ?";
            case "active_devices" ->
                    "SELECT COUNT(*) FROM sys_device_session WHERE last_active >= ? AND last_active < ?";
            case "total_users_eod" -> "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND create_time < ?";
            case "group_messages" -> """
                    SELECT COUNT(*) FROM im_message m
                    INNER JOIN im_conversation c ON c.id = m.conversation_id AND c.type = 2 AND c.deleted = 0
                    WHERE m.deleted = 0 AND m.create_time >= ? AND m.create_time < ?
                    """;
            default -> throw new IllegalArgumentException("unsupported metric: " + metric);
        };
        Object[] args = "total_users_eod".equals(metric)
                ? new Object[] {dayEnd}
                : new Object[] {dayStart, dayEnd};
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    private int captureHeatmapBuckets(LocalDate snapshotDate, java.util.Date dayStart, java.util.Date dayEnd,
                                      String metricKey) {
        String sql = "messages".equals(metricKey) ? MESSAGE_HEATMAP_SQL : LOGIN_HEATMAP_SQL;
        int[] inserted = {0};
        jdbcTemplate.query(sql, rs -> {
            statisticSnapshotMapper.insert(SysAdminStatisticSnapshot.builder()
                    .snapshotDate(Date.valueOf(snapshotDate))
                    .metricDomain(DOMAIN_HEATMAP)
                    .metricKey(metricKey)
                    .dimensionKey(String.valueOf(rs.getInt("wd")))
                    .dimensionValue(String.valueOf(rs.getInt("h")))
                    .metricValue(rs.getLong("c"))
                    .build());
            inserted[0]++;
        }, dayStart, dayEnd);
        return inserted[0];
    }

    private String serializeSummary(AdminDashboardSummaryVO summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize dashboard summary snapshot: {}", ex.getMessage());
            return "{}";
        }
    }
}
