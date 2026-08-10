package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminBiQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBiDrillTargetVO;
import com.linkx.server.controller.admin.vo.AdminBiMetricVO;
import com.linkx.server.controller.admin.vo.AdminBiQueryVO;
import com.linkx.server.controller.admin.vo.AdminBigScreenVO;
import com.linkx.server.controller.admin.vo.AdminChartSeriesVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminStatisticBreakdownVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.admin.AdminBiService;
import com.linkx.server.service.admin.AdminDashboardService;
import com.linkx.server.service.admin.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminBiServiceImpl implements AdminBiService {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 14;
    private static final int SPARK_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;
    private final AdminStatisticsService adminStatisticsService;
    private final AdminDashboardService adminDashboardService;

    @Override
    public List<AdminBiMetricVO> listMetrics() {
        return List.of(
                metric("new_users", "新增用户", List.of("none"), drill("/admin/users", Map.of())),
                metric("logins", "登录成功", List.of("none"), drill("/admin/login-logs", Map.of("success", "1"))),
                metric("messages", "消息量", List.of("none"), drill("/admin/system-monitor/api-stats", Map.of())),
                metric("feedback", "反馈", List.of("none", "feedback_type", "feedback_status"),
                        drill("/admin/feedback", Map.of())),
                metric("risk_events", "风险事件", List.of("none", "risk_level"),
                        drill("/admin/risk-events", Map.of())),
                metric("reviews", "审核任务", List.of("none"), drill("/admin/reviews", Map.of("status", "pending")))
        );
    }

    @Override
    public AdminBiQueryVO query(AdminBiQueryDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getMetric())) {
            throw new CustomException(400, "metric required");
        }
        String metric = dto.getMetric().trim().toLowerCase(Locale.ROOT);
        String dimension = normalizeDimension(dto.getDimension());
        int days = normalizeDays(dto.getDays());
        boolean compare = Boolean.TRUE.equals(dto.getComparePrevious());

        if (!"none".equals(dimension)) {
            return buildBreakdownQuery(metric, dimension, days, compare);
        }

        Date currentStart = startOfDaysAgo(days);
        AdminTrendVO current = buildTrend(days, currentStart, metric);

        AdminTrendVO previous = null;
        Double deltaPct = null;
        if (compare) {
            Date prevStart = startOfDaysAgo(days * 2);
            Date prevEnd = startOfDaysAgo(days);
            previous = buildTrend(days, prevStart, prevEnd, metric);
            deltaPct = calcDeltaPct(sumSeries(current), sumSeries(previous));
        }

        return AdminBiQueryVO.builder()
                .metric(metric)
                .dimension(dimension)
                .days(days)
                .labels(current.getLabels())
                .series(current.getSeries())
                .compareSeries(previous != null ? previous.getSeries() : null)
                .compareTotalDeltaPct(deltaPct)
                .drillTarget(drillTargetFor(metric))
                .build();
    }

    @Override
    public AdminBigScreenVO bigScreenData() {
        AdminDashboardSummaryVO summary = adminDashboardService.summary(null);
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        long todayRisk = adminStatisticsService.countRiskEventsSince(todayStart);
        return AdminBigScreenVO.builder()
                .refreshedAt(new Date())
                .totalUsers(summary.getTotalUsers())
                .dau(summary.getDau())
                .onlineDevices(summary.getOnlineDevices())
                .pendingFeedback(summary.getPendingFeedback())
                .pendingReviews(summary.getPendingReviews())
                .todayMessages(countTodayMessages())
                .todayLogins(countTodayLogins())
                .todayRiskEvents(todayRisk)
                .tickers(loadTickers())
                .kpiTrends(buildKpiTrends())
                .build();
    }

    private Map<String, List<Long>> buildKpiTrends() {
        Map<String, List<Long>> trends = new LinkedHashMap<>();
        trends.put("totalUsers", sparkCumulativeUsers(SPARK_DAYS));
        trends.put("dau", sparkDailyDistinctLogins(SPARK_DAYS));
        trends.put("onlineDevices", sparkDailyActiveDevices(SPARK_DAYS));
        trends.put("todayMessages", sparkFromMetric("messages", SPARK_DAYS));
        trends.put("todayLogins", sparkFromMetric("logins", SPARK_DAYS));
        trends.put("todayRiskEvents", sparkFromMetric("risk_events", SPARK_DAYS));
        trends.put("pendingFeedback", sparkFromMetric("feedback", SPARK_DAYS));
        trends.put("pendingReviews", sparkFromMetric("reviews", SPARK_DAYS));
        return trends;
    }

    private List<Long> sparkFromMetric(String metric, int days) {
        Date start = startOfDaysAgo(days);
        return fillSparkDays(dailyCounts(metric, start, null), days);
    }

    private List<Long> sparkCumulativeUsers(int days) {
        List<Long> points = new ArrayList<>(days);
        LocalDate end = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = end.minusDays(i);
            Date dayEnd = Date.from(day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND create_time < ?",
                    Long.class,
                    dayEnd);
            points.add(n == null ? 0L : n);
        }
        return points;
    }

    private List<Long> sparkDailyDistinctLogins(int days) {
        Date start = startOfDaysAgo(days);
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT DATE(create_time) AS d, COUNT(DISTINCT user_id) AS c
                FROM sys_login_audit WHERE success = 1 AND create_time >= ?
                GROUP BY DATE(create_time)
                """,
                rs -> {
                    java.sql.Date d = rs.getDate("d");
                    if (d != null) {
                        map.put(d.toLocalDate(), rs.getLong("c"));
                    }
                },
                start);
        return fillSparkDays(map, days);
    }

    private List<Long> sparkDailyActiveDevices(int days) {
        Date start = startOfDaysAgo(days);
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT DATE(last_active) AS d, COUNT(*) AS c
                FROM sys_device_session WHERE last_active >= ?
                GROUP BY DATE(last_active)
                """,
                rs -> {
                    java.sql.Date d = rs.getDate("d");
                    if (d != null) {
                        map.put(d.toLocalDate(), rs.getLong("c"));
                    }
                },
                start);
        return fillSparkDays(map, days);
    }

    private static List<Long> fillSparkDays(Map<LocalDate, Long> counts, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<Long> points = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            points.add(counts.getOrDefault(start.plusDays(i), 0L));
        }
        return points;
    }

    private List<AdminBigScreenVO.TickerItem> loadTickers() {
        List<AdminBigScreenVO.TickerItem> items = new ArrayList<>();
        jdbcTemplate.query(
                """
                SELECT 'risk_event' AS type, title, id AS related_id, create_time AS ts
                FROM sys_risk_event ORDER BY create_time DESC LIMIT 8
                """,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> appendTicker(items, rs));
        jdbcTemplate.query(
                """
                SELECT 'feedback' AS type, content AS title, id AS related_id, create_time AS ts
                FROM sys_feedback ORDER BY create_time DESC LIMIT 5
                """,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> appendTicker(items, rs));
        items.sort((a, b) -> Long.compare(b.getTs(), a.getTs()));
        return items.size() > 15 ? items.subList(0, 15) : items;
    }

    private static void appendTicker(List<AdminBigScreenVO.TickerItem> items, java.sql.ResultSet rs)
            throws java.sql.SQLException {
        String title = rs.getString("title");
        if (!StringUtils.hasText(title)) {
            title = rs.getString("type");
        }
        if (title != null && title.length() > 80) {
            title = title.substring(0, 80) + "…";
        }
        items.add(AdminBigScreenVO.TickerItem.builder()
                .type(rs.getString("type"))
                .title(title)
                .relatedId(rs.getLong("related_id"))
                .ts(rs.getTimestamp("ts").getTime())
                .build());
    }

    private AdminBiQueryVO buildBreakdownQuery(String metric, String dimension, int days, boolean compare) {
        Date start = startOfDaysAgo(days);
        List<AdminStatisticBreakdownVO> breakdown = queryBreakdown(metric, dimension, start);
        long currentTotal = breakdown.stream().mapToLong(AdminStatisticBreakdownVO::getValue).sum();

        List<AdminStatisticBreakdownVO> compareBreakdown = null;
        Double deltaPct = null;
        if (compare) {
            Date prevStart = startOfDaysAgo(days * 2);
            Date prevEnd = startOfDaysAgo(days);
            compareBreakdown = queryBreakdown(metric, dimension, prevStart, prevEnd);
            long prevTotal = compareBreakdown.stream().mapToLong(AdminStatisticBreakdownVO::getValue).sum();
            deltaPct = calcDeltaPct(currentTotal, prevTotal);
        }

        List<String> labels = breakdown.stream().map(AdminStatisticBreakdownVO::getName).toList();
        List<Long> data = breakdown.stream().map(AdminStatisticBreakdownVO::getValue).toList();
        List<AdminChartSeriesVO> series = List.of(AdminChartSeriesVO.builder()
                .key("current")
                .name("当前周期")
                .data(data)
                .build());

        List<AdminChartSeriesVO> compareSeries = null;
        if (compareBreakdown != null) {
            Map<String, Long> prevMap = new HashMap<>();
            for (AdminStatisticBreakdownVO item : compareBreakdown) {
                prevMap.put(item.getKey(), item.getValue());
            }
            List<Long> compareData = breakdown.stream()
                    .map(b -> prevMap.getOrDefault(b.getKey(), 0L))
                    .toList();
            compareSeries = List.of(AdminChartSeriesVO.builder()
                    .key("previous")
                    .name("上一周期")
                    .data(compareData)
                    .build());
        }

        return AdminBiQueryVO.builder()
                .metric(metric)
                .dimension(dimension)
                .days(days)
                .labels(labels)
                .series(series)
                .compareSeries(compareSeries)
                .compareTotalDeltaPct(deltaPct)
                .breakdown(breakdown)
                .drillTarget(drillTargetFor(metric))
                .build();
    }

    private List<AdminStatisticBreakdownVO> queryBreakdown(String metric, String dimension, Date start) {
        return queryBreakdown(metric, dimension, start, null);
    }

    private List<AdminStatisticBreakdownVO> queryBreakdown(String metric, String dimension, Date start, Date end) {
        String sql = breakdownSql(metric, dimension, end != null);
        Object[] args = end != null ? new Object[] {start, end} : new Object[] {start};
        List<AdminStatisticBreakdownVO> items = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String key = rs.getString("k");
            String name = rs.getString("n");
            long value = rs.getLong("c");
            items.add(AdminStatisticBreakdownVO.builder()
                    .key(key == null ? "unknown" : key)
                    .name(StringUtils.hasText(name) ? name : key)
                    .value(value)
                    .build());
        }, args);
        return items;
    }

    private String breakdownSql(String metric, String dimension, boolean ranged) {
        String timeClause = ranged ? "create_time >= ? AND create_time < ?" : "create_time >= ?";
        if ("feedback".equals(metric)) {
            if ("feedback_type".equals(dimension)) {
                return "SELECT type AS k, type AS n, COUNT(*) AS c FROM sys_feedback WHERE " + timeClause
                        + " GROUP BY type ORDER BY c DESC";
            }
            if ("feedback_status".equals(dimension)) {
                return "SELECT status AS k, status AS n, COUNT(*) AS c FROM sys_feedback WHERE " + timeClause
                        + " GROUP BY status ORDER BY c DESC";
            }
        }
        if ("risk_events".equals(metric) && "risk_level".equals(dimension)) {
            return "SELECT risk_level AS k, risk_level AS n, COUNT(*) AS c FROM sys_risk_event WHERE " + timeClause
                    + " GROUP BY risk_level ORDER BY c DESC";
        }
        throw new CustomException(400, "unsupported dimension for metric");
    }

    private AdminTrendVO buildTrend(int days, Date start, String metric) {
        return buildTrend(days, start, null, metric);
    }

    private AdminTrendVO buildTrend(int days, Date start, Date end, String metric) {
        Map<LocalDate, Long> counts = dailyCounts(metric, start, end);
        List<LocalDate> dates = new ArrayList<>(days);
        LocalDate endDate = end != null
                ? end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1)
                : LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            dates.add(startDate.plusDays(i));
        }
        List<String> labels = dates.stream().map(LABEL_FMT::format).toList();
        List<Long> data = dates.stream().map(d -> counts.getOrDefault(d, 0L)).toList();
        return AdminTrendVO.builder()
                .labels(labels)
                .series(List.of(AdminChartSeriesVO.builder()
                        .key(metric)
                        .name(seriesName(metric))
                        .data(data)
                        .build()))
                .build();
    }

    private Map<LocalDate, Long> dailyCounts(String metric, Date start, Date end) {
        String sql = trendSql(metric, end != null);
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        Object[] args = end != null ? new Object[] {start, end} : new Object[] {start};
        jdbcTemplate.query(sql, rs -> {
            java.sql.Date d = rs.getDate("d");
            if (d != null) {
                map.put(d.toLocalDate(), rs.getLong("c"));
            }
        }, args);
        return map;
    }

    private String trendSql(String metric, boolean ranged) {
        String timeClause = ranged ? "create_time >= ? AND create_time < ?" : "create_time >= ?";
        return switch (metric) {
            case "new_users" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_user WHERE deleted = 0 AND "
                    + timeClause + " GROUP BY DATE(create_time)";
            case "logins" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_login_audit WHERE success = 1 AND "
                    + timeClause + " GROUP BY DATE(create_time)";
            case "messages" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM im_message WHERE "
                    + timeClause + " GROUP BY DATE(create_time)";
            case "feedback" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_feedback WHERE "
                    + timeClause + " GROUP BY DATE(create_time)";
            case "risk_events" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_risk_event WHERE "
                    + timeClause + " GROUP BY DATE(create_time)";
            case "reviews" -> "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_review_task WHERE "
                    + timeClause + " GROUP BY DATE(create_time)";
            default -> throw new CustomException(400, "unsupported metric");
        };
    }

    private long countTodayMessages() {
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM im_message WHERE create_time >= ?",
                Long.class,
                todayStart);
        return n == null ? 0L : n;
    }

    private long countTodayLogins() {
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_audit WHERE success = 1 AND create_time >= ?",
                Long.class,
                todayStart);
        return n == null ? 0L : n;
    }

    private static long sumSeries(AdminTrendVO trend) {
        if (trend == null || trend.getSeries() == null || trend.getSeries().isEmpty()) {
            return 0L;
        }
        return trend.getSeries().get(0).getData().stream().mapToLong(Long::longValue).sum();
    }

    private static Double calcDeltaPct(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }

    private static int normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }

    private static String normalizeDimension(String dimension) {
        if (!StringUtils.hasText(dimension) || "none".equalsIgnoreCase(dimension.trim())) {
            return "none";
        }
        return dimension.trim().toLowerCase(Locale.ROOT);
    }

    private Date startOfDaysAgo(int days) {
        return Date.from(LocalDate.now().minusDays(days - 1L)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String seriesName(String metric) {
        return switch (metric) {
            case "new_users" -> "新增用户";
            case "logins" -> "登录成功";
            case "messages" -> "消息量";
            case "feedback" -> "反馈";
            case "risk_events" -> "风险事件";
            case "reviews" -> "审核任务";
            default -> metric;
        };
    }

    private static AdminBiMetricVO metric(String key, String name, List<String> dimensions,
                                          AdminBiDrillTargetVO drill) {
        return AdminBiMetricVO.builder().key(key).name(name).dimensions(dimensions).drillTarget(drill).build();
    }

    private static AdminBiDrillTargetVO drillTargetFor(String metric) {
        return drillTargetForMetric(metric);
    }

    private static AdminBiDrillTargetVO drillTargetForMetric(String metric) {
        return switch (metric) {
            case "new_users" -> drill("/admin/users", Map.of());
            case "logins" -> drill("/admin/login-logs", Map.of("success", "1"));
            case "messages" -> drill("/admin/system-monitor/api-stats", Map.of());
            case "feedback" -> drill("/admin/feedback", Map.of());
            case "risk_events" -> drill("/admin/risk-events", Map.of());
            case "reviews" -> drill("/admin/reviews", Map.of("status", "pending"));
            default -> null;
        };
    }

    private static AdminBiDrillTargetVO drill(String route, Map<String, String> query) {
        return AdminBiDrillTargetVO.builder().route(route).query(query).build();
    }
}
