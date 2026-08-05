package com.linkx.server.service.admin.impl;

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

    private static final String DOMAIN_STATISTIC = "statistic";
    private static final List<String> METRICS = List.of(
            "new_users", "logins", "messages", "feedback", "risk_events", "reviews");

    private final JdbcTemplate jdbcTemplate;
    private final SysAdminStatisticSnapshotMapper statisticSnapshotMapper;
    private final SysAdminDashboardSnapshotMapper dashboardSnapshotMapper;
    private final AdminDashboardService adminDashboardService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public int captureYesterdaySnapshots() {
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        java.util.Date dayStart = java.util.Date.from(
                snapshotDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.Date dayEnd = java.util.Date.from(
                snapshotDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        purgeExisting(snapshotDate);

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

        dashboardSnapshotMapper.insert(SysAdminDashboardSnapshot.builder()
                .snapshotDate(Date.valueOf(snapshotDate))
                .summaryJson(serializeSummary(adminDashboardService.summary(null)))
                .build());
        return count + 1;
    }

    private void purgeExisting(LocalDate snapshotDate) {
        Date sqlDate = Date.valueOf(snapshotDate);
        statisticSnapshotMapper.deleteByQuery(QueryWrapper.create()
                .where(SysAdminStatisticSnapshot::getSnapshotDate).eq(sqlDate));
        dashboardSnapshotMapper.deleteByQuery(QueryWrapper.create()
                .where(SysAdminDashboardSnapshot::getSnapshotDate).eq(sqlDate));
    }

    private long countMetric(String metric, java.util.Date dayStart, java.util.Date dayEnd) {
        String sql = switch (metric) {
            case "new_users" -> "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND create_time >= ? AND create_time < ?";
            case "logins" -> "SELECT COUNT(*) FROM sys_login_audit WHERE success = 1 AND create_time >= ? AND create_time < ?";
            case "messages" -> "SELECT COUNT(*) FROM im_message WHERE create_time >= ? AND create_time < ?";
            case "feedback" -> "SELECT COUNT(*) FROM sys_feedback WHERE create_time >= ? AND create_time < ?";
            case "risk_events" -> "SELECT COUNT(*) FROM sys_risk_event WHERE create_time >= ? AND create_time < ?";
            case "reviews" -> "SELECT COUNT(*) FROM sys_review_task WHERE create_time >= ? AND create_time < ?";
            default -> throw new IllegalArgumentException("unsupported metric: " + metric);
        };
        Long n = jdbcTemplate.queryForObject(sql, Long.class, dayStart, dayEnd);
        return n == null ? 0L : n;
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
