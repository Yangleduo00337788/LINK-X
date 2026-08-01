package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminActivityHeatmapVO;
import com.linkx.server.controller.admin.vo.AdminChartSeriesVO;
import com.linkx.server.controller.admin.vo.AdminGroupActivityItemVO;
import com.linkx.server.controller.admin.vo.AdminStatisticBreakdownVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticGroupVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.AdminStatisticsService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticsServiceImpl implements AdminStatisticsService {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 14;

    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper sysUserMapper;
    private final DeviceSessionMapper deviceSessionMapper;
    private final FeedbackMapper feedbackMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final ImMessageMapper imMessageMapper;
    private final CloudFileMapper cloudFileMapper;
    private final SysReviewTaskMapper sysReviewTaskMapper;
    private final SysRiskEventMapper sysRiskEventMapper;
    private final AdminReviewService adminReviewService;

    @Override
    public AdminStatisticOverviewVO overview(int days) {
        normalizeDays(days);
        Date todayStart = startOfToday();
        Date dayAgo = startOfDaysAgo(1);

        long totalUsers = sysUserMapper.selectCountByQuery(QueryWrapper.create());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        long activeUsers = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getUpdateTime).ge(cal.getTime()));
        long onlineDevices = countOnlineDevices();
        long pendingFeedback = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending"));
        long pendingReviews = adminReviewService.countPending();
        long riskEvents = countRiskEventsSince(dayAgo);
        long todayNewUsers = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getCreateTime).ge(todayStart));
        long todayMessages = imMessageMapper.selectCountByQuery(
                QueryWrapper.create().where(ImMessage::getCreateTime).ge(todayStart));
        long todayLogins = sysLoginAuditMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysLoginAudit::getCreateTime).ge(todayStart)
                        .and(SysLoginAudit::getSuccess).eq(1));
        long totalMessages = imMessageMapper.selectCountByQuery(QueryWrapper.create());
        long totalUploads = cloudFileMapper.selectCountByQuery(QueryWrapper.create());
        long closedFeedback = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("closed"));

        return AdminStatisticOverviewVO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineDevices(onlineDevices)
                .pendingFeedback(pendingFeedback)
                .pendingReviews(pendingReviews)
                .riskEvents(riskEvents)
                .todayNewUsers(todayNewUsers)
                .todayMessages(todayMessages)
                .todayLogins(todayLogins)
                .totalMessages(totalMessages)
                .totalUploads(totalUploads)
                .closedFeedback(closedFeedback)
                .build();
    }

    @Override
    public AdminStatisticUserVO users(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);

        AdminTrendVO trend = buildTrend(range,
                series("newUsers", "新增用户",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_user "
                                + "WHERE deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)),
                series("loginSuccess", "登录成功",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_login_audit "
                                + "WHERE success = 1 AND create_time >= ? GROUP BY DATE(create_time)", start)),
                series("loginFail", "登录失败",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_login_audit "
                                + "WHERE success = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)));

        long normal = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getStatus).eq(1));
        long frozen = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getStatus).eq(0));

        List<AdminStatisticBreakdownVO> statusBreakdown = List.of(
                AdminStatisticBreakdownVO.builder().key("normal").name("正常").value(normal).build(),
                AdminStatisticBreakdownVO.builder().key("frozen").name("禁用").value(frozen).build());

        long newUsersInRange = sumSeries(trend, "newUsers");
        long loginSuccessInRange = sumSeries(trend, "loginSuccess");
        long loginFailInRange = sumSeries(trend, "loginFail");

        return AdminStatisticUserVO.builder()
                .trend(trend)
                .statusBreakdown(statusBreakdown)
                .newUsersInRange(newUsersInRange)
                .loginSuccessInRange(loginSuccessInRange)
                .loginFailInRange(loginFailInRange)
                .build();
    }

    @Override
    public AdminStatisticContentVO content(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);

        AdminTrendVO trend = buildTrend(range,
                series("messages", "消息量",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM im_message "
                                + "WHERE deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)),
                series("moments", "朋友圈",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM moments_post "
                                + "WHERE create_time >= ? GROUP BY DATE(create_time)", start)),
                series("uploads", "文件上传",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM cloud_file "
                                + "WHERE deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)));

        return AdminStatisticContentVO.builder()
                .trend(trend)
                .messagesInRange(sumSeries(trend, "messages"))
                .momentsInRange(sumSeries(trend, "moments"))
                .uploadsInRange(sumSeries(trend, "uploads"))
                .build();
    }

    @Override
    public AdminStatisticRiskVO risk(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);

        AdminTrendVO trend = buildTrend(range,
                series("sensitive", "敏感词命中",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_risk_event "
                                + "WHERE event_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH, start)),
                series("storm", "消息风暴",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_risk_event "
                                + "WHERE event_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysRiskEvent.TYPE_MESSAGE_STORM, start)),
                series("loginLock", "登录锁定",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_risk_event "
                                + "WHERE event_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysRiskEvent.TYPE_LOGIN_LOCK, start)),
                series("rateLimit", "限流触发",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_risk_event "
                                + "WHERE event_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysRiskEvent.TYPE_RATE_LIMIT, start)),
                series("reviews", "审核任务",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_review_task "
                                + "WHERE create_time >= ? GROUP BY DATE(create_time)", start)));

        long pending = sysReviewTaskMapper.selectCountByQuery(
                QueryWrapper.create().where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING));
        long approved = sysReviewTaskMapper.selectCountByQuery(
                QueryWrapper.create().where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_APPROVED));
        long rejected = sysReviewTaskMapper.selectCountByQuery(
                QueryWrapper.create().where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_REJECTED));

        List<AdminStatisticBreakdownVO> reviewStatus = List.of(
                AdminStatisticBreakdownVO.builder().key("pending").name("待审核").value(pending).build(),
                AdminStatisticBreakdownVO.builder().key("approved").name("已通过").value(approved).build(),
                AdminStatisticBreakdownVO.builder().key("rejected").name("已拒绝").value(rejected).build());

        AdminTrendVO reviewEfficiencyTrend = buildTrend(range,
                series("reviewCreated", "新建审核",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_review_task "
                                + "WHERE create_time >= ? GROUP BY DATE(create_time)", start)),
                series("reviewResolved", "结案审核",
                        dailyCounts("SELECT DATE(resolved_at) AS d, COUNT(*) AS c FROM sys_review_task "
                                + "WHERE resolved_at IS NOT NULL AND resolved_at >= ? "
                                + "AND status IN (?, ?) GROUP BY DATE(resolved_at)",
                                start, SysReviewTask.STATUS_APPROVED, SysReviewTask.STATUS_REJECTED)));

        Double avgHandleMinutes = avgReviewHandleMinutes(start);

        Date ago24h = Date.from(java.time.Instant.now().minus(java.time.Duration.ofHours(24)));
        Date ago72h = Date.from(java.time.Instant.now().minus(java.time.Duration.ofHours(72)));
        long pendingOver24h = sysReviewTaskMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING)
                        .and(SysReviewTask::getCreateTime).le(ago24h));
        long pendingOver72h = sysReviewTaskMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING)
                        .and(SysReviewTask::getCreateTime).le(ago72h));

        return AdminStatisticRiskVO.builder()
                .trend(trend)
                .reviewEfficiencyTrend(reviewEfficiencyTrend)
                .reviewStatusBreakdown(reviewStatus)
                .sensitiveHitsInRange(sumSeries(trend, "sensitive"))
                .messageStormsInRange(sumSeries(trend, "storm"))
                .loginLocksInRange(sumSeries(trend, "loginLock"))
                .rateLimitsInRange(sumSeries(trend, "rateLimit"))
                .pendingReviews(pending)
                .resolvedReviewsInRange(sumSeries(reviewEfficiencyTrend, "reviewResolved"))
                .avgHandleMinutesInRange(avgHandleMinutes)
                .pendingOver24h(pendingOver24h)
                .pendingOver72h(pendingOver72h)
                .build();
    }

    @Override
    public AdminStatisticFeedbackVO feedback(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);

        AdminTrendVO trend = buildTrend(range,
                series("created", "新增反馈",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_feedback "
                                + "WHERE create_time >= ? GROUP BY DATE(create_time)", start)),
                series("replied", "已回复",
                        dailyCounts("SELECT DATE(reply_time) AS d, COUNT(*) AS c FROM sys_feedback "
                                + "WHERE reply_time IS NOT NULL AND reply_time >= ? GROUP BY DATE(reply_time)", start)));

        long pending = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending"));
        long replied = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("replied"));
        long closed = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("closed"));

        List<AdminStatisticBreakdownVO> statusBreakdown = List.of(
                AdminStatisticBreakdownVO.builder().key("pending").name("待处理").value(pending).build(),
                AdminStatisticBreakdownVO.builder().key("replied").name("已回复").value(replied).build(),
                AdminStatisticBreakdownVO.builder().key("closed").name("已关闭").value(closed).build());

        return AdminStatisticFeedbackVO.builder()
                .trend(trend)
                .statusBreakdown(statusBreakdown)
                .createdInRange(sumSeries(trend, "created"))
                .repliedInRange(sumSeries(trend, "replied"))
                .closedInRange(closed)
                .build();
    }

    @Override
    public AdminStatisticGroupVO groups(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);

        long totalGroups = countLong(
                "SELECT COUNT(*) FROM im_conversation WHERE type = ? AND deleted = 0",
                ImConversation.TYPE_GROUP);
        Long activeGroups = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT m.conversation_id) FROM im_message m "
                        + "INNER JOIN im_conversation c ON c.id = m.conversation_id "
                        + "WHERE c.type = ? AND c.deleted = 0 AND m.deleted = 0 AND m.create_time >= ?",
                Long.class, ImConversation.TYPE_GROUP, start);

        AdminTrendVO trend = buildTrend(range,
                series("newGroups", "新建群",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM im_conversation "
                                + "WHERE type = ? AND deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)",
                                ImConversation.TYPE_GROUP, start)),
                series("groupMessages", "群消息",
                        dailyCounts("SELECT DATE(m.create_time) AS d, COUNT(*) AS c FROM im_message m "
                                + "INNER JOIN im_conversation c ON c.id = m.conversation_id "
                                + "WHERE c.type = ? AND c.deleted = 0 AND m.deleted = 0 AND m.create_time >= ? "
                                + "GROUP BY DATE(m.create_time)",
                                ImConversation.TYPE_GROUP, start)));

        List<AdminGroupActivityItemVO> topGroups = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT c.id AS id, c.name AS name, c.last_message_time AS last_msg, "
                        + "COUNT(m.id) AS msg_cnt, "
                        + "(SELECT COUNT(*) FROM im_conversation_member cm "
                        + " WHERE cm.conversation_id = c.id AND cm.deleted = 0) AS member_cnt "
                        + "FROM im_conversation c "
                        + "LEFT JOIN im_message m ON m.conversation_id = c.id "
                        + " AND m.deleted = 0 AND m.create_time >= ? "
                        + "WHERE c.type = ? AND c.deleted = 0 "
                        + "GROUP BY c.id, c.name, c.last_message_time "
                        + "ORDER BY msg_cnt DESC, c.id DESC "
                        + "LIMIT 10",
                rs -> {
                    topGroups.add(AdminGroupActivityItemVO.builder()
                            .id(rs.getLong("id"))
                            .name(rs.getString("name"))
                            .messageCount(rs.getLong("msg_cnt"))
                            .memberCount(rs.getLong("member_cnt"))
                            .lastMessageTime(rs.getTimestamp("last_msg"))
                            .build());
                },
                start, ImConversation.TYPE_GROUP);

        return AdminStatisticGroupVO.builder()
                .totalGroups(totalGroups)
                .activeGroupsInRange(activeGroups == null ? 0L : activeGroups)
                .newGroupsInRange(sumSeries(trend, "newGroups"))
                .groupMessagesInRange(sumSeries(trend, "groupMessages"))
                .trend(trend)
                .topGroups(topGroups)
                .build();
    }

    @Override
    public AdminActivityHeatmapVO activityHeatmap(int days, String metric) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);
        String m = normalizeHeatmapMetric(metric);
        // MySQL DAYOFWEEK: 1=周日..7=周六 → 周一=0..周日=6
        String sql = "messages".equals(m)
                ? "SELECT MOD(DAYOFWEEK(create_time) + 5, 7) AS wd, HOUR(create_time) AS h, COUNT(*) AS c "
                + "FROM im_message WHERE deleted = 0 AND create_time >= ? GROUP BY wd, h"
                : "SELECT MOD(DAYOFWEEK(create_time) + 5, 7) AS wd, HOUR(create_time) AS h, COUNT(*) AS c "
                + "FROM sys_login_audit WHERE success = 1 AND create_time >= ? GROUP BY wd, h";

        long[][] matrix = new long[7][24];
        jdbcTemplate.query(sql, rs -> {
            int wd = rs.getInt("wd");
            int h = rs.getInt("h");
            long c = rs.getLong("c");
            if (wd >= 0 && wd < 7 && h >= 0 && h < 24) {
                matrix[wd][h] = c;
            }
        }, start);

        List<List<Long>> cells = new ArrayList<>(7 * 24);
        long max = 0L;
        long total = 0L;
        for (int wd = 0; wd < 7; wd++) {
            for (int h = 0; h < 24; h++) {
                long c = matrix[wd][h];
                cells.add(List.of((long) wd, (long) h, c));
                if (c > max) {
                    max = c;
                }
                total += c;
            }
        }
        return AdminActivityHeatmapVO.builder()
                .metric(m)
                .days(range)
                .maxValue(max)
                .total(total)
                .cells(cells)
                .build();
    }

    @Override
    public AdminTrendVO dashboardTrends(int days) {
        int range = normalizeDays(days);
        Date start = startOfDaysAgo(range);
        return buildTrend(range,
                series("newUsers", "新增用户",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_user "
                                + "WHERE deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)),
                series("messages", "消息量",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM im_message "
                                + "WHERE deleted = 0 AND create_time >= ? GROUP BY DATE(create_time)", start)),
                series("logins", "登录成功",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_login_audit "
                                + "WHERE success = 1 AND create_time >= ? GROUP BY DATE(create_time)", start)));
    }

    @Override
    public long countOnlineDevices() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -15);
        return deviceSessionMapper.selectCountByQuery(
                QueryWrapper.create().where("last_active >= ?", cal.getTime()));
    }

    @Override
    public long countRiskEventsSince(Date since) {
        QueryWrapper qw = QueryWrapper.create();
        if (since != null) {
            qw.where(SysRiskEvent::getCreateTime).ge(since);
        }
        return sysRiskEventMapper.selectCountByQuery(qw);
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }

    private String normalizeHeatmapMetric(String metric) {
        if (metric != null && "messages".equalsIgnoreCase(metric.trim())) {
            return "messages";
        }
        return "logins";
    }

    private long countLong(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    /** 区间内已结案审核的平均处理时长（分钟）。 */
    private Double avgReviewHandleMinutes(Date since) {
        List<SysReviewTask> rows = sysReviewTaskMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getResolvedAt).ge(since)
                        .and(SysReviewTask::getStatus).in(
                                SysReviewTask.STATUS_APPROVED, SysReviewTask.STATUS_REJECTED));
        double sum = 0;
        int n = 0;
        for (SysReviewTask t : rows) {
            if (t.getCreateTime() == null || t.getResolvedAt() == null) {
                continue;
            }
            long minutes = (t.getResolvedAt().getTime() - t.getCreateTime().getTime()) / 60_000L;
            if (minutes < 0) {
                continue;
            }
            sum += minutes;
            n++;
        }
        if (n == 0) {
            return null;
        }
        return Math.round((sum / n) * 10.0) / 10.0;
    }

    private Date startOfToday() {
        return Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date startOfDaysAgo(int days) {
        return Date.from(LocalDate.now().minusDays(days - 1L)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Map<LocalDate, Long> dailyCounts(String sql, Object... args) {
        Map<LocalDate, Long> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            java.sql.Date d = rs.getDate("d");
            if (d != null) {
                map.put(d.toLocalDate(), rs.getLong("c"));
            }
        }, args);
        return map;
    }

    @SafeVarargs
    private final AdminTrendVO buildTrend(int days, NamedSeries... seriesList) {
        List<LocalDate> dates = new ArrayList<>(days);
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            dates.add(start.plusDays(i));
        }
        List<String> labels = dates.stream().map(LABEL_FMT::format).toList();
        List<AdminChartSeriesVO> series = new ArrayList<>();
        for (NamedSeries ns : seriesList) {
            List<Long> data = new ArrayList<>(days);
            for (LocalDate d : dates) {
                data.add(ns.counts().getOrDefault(d, 0L));
            }
            series.add(AdminChartSeriesVO.builder()
                    .key(ns.key())
                    .name(ns.name())
                    .data(data)
                    .build());
        }
        return AdminTrendVO.builder().labels(labels).series(series).build();
    }

    private NamedSeries series(String key, String name, Map<LocalDate, Long> counts) {
        return new NamedSeries(key, name, counts);
    }

    private long sumSeries(AdminTrendVO trend, String key) {
        if (trend == null || trend.getSeries() == null) {
            return 0L;
        }
        return trend.getSeries().stream()
                .filter(s -> key.equals(s.getKey()))
                .findFirst()
                .map(s -> s.getData() == null ? 0L : s.getData().stream().mapToLong(Long::longValue).sum())
                .orElse(0L);
    }

    private record NamedSeries(String key, String name, Map<LocalDate, Long> counts) {
    }
}
