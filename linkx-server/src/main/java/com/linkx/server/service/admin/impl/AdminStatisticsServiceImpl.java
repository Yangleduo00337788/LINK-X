package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminChartSeriesVO;
import com.linkx.server.controller.admin.vo.AdminStatisticBreakdownVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
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
    private final SysAuditLogMapper sysAuditLogMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final ImMessageMapper imMessageMapper;
    private final CloudFileMapper cloudFileMapper;
    private final SysReviewTaskMapper sysReviewTaskMapper;
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
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_audit_log "
                                + "WHERE operation_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysAuditLog.OperationType.SENSITIVE_WORD_MATCH.name(), start)),
                series("storm", "消息风暴",
                        dailyCounts("SELECT DATE(create_time) AS d, COUNT(*) AS c FROM sys_audit_log "
                                + "WHERE operation_type = ? AND create_time >= ? GROUP BY DATE(create_time)",
                                SysAuditLog.OperationType.MESSAGE_STORM.name(), start)),
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

        return AdminStatisticRiskVO.builder()
                .trend(trend)
                .reviewStatusBreakdown(reviewStatus)
                .sensitiveHitsInRange(sumSeries(trend, "sensitive"))
                .messageStormsInRange(sumSeries(trend, "storm"))
                .pendingReviews(pending)
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
        return sysAuditLogMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysAuditLog::getCreateTime).ge(since)
                        .and(SysAuditLog::getOperationType).in(
                                SysAuditLog.OperationType.SENSITIVE_WORD_MATCH.name(),
                                SysAuditLog.OperationType.MESSAGE_STORM.name()));
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
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
