package com.linkx.server.service.admin.impl;

import com.linkx.server.common.RbacConstants;
import com.linkx.server.controller.admin.vo.AdminDashboardRealtimeVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminPendingTaskVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminDashboardService;
import com.linkx.server.service.admin.AdminFeedbackService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.linkx.server.service.admin.AdminStatisticsService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final SysUserMapper sysUserMapper;
    private final FeedbackMapper feedbackMapper;
    private final ImMessageMapper imMessageMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final AdminFeedbackService adminFeedbackService;
    private final AdminReviewService adminReviewService;
    private final AdminRiskEventService adminRiskEventService;
    private final AdminStatisticsService adminStatisticsService;
    private final RbacService rbacService;

    @Override
    public AdminDashboardSummaryVO summary(Long operatorUserId) {
        boolean canFeedback = hasPerm(operatorUserId, "admin:feedback:list");
        boolean canReview = hasPerm(operatorUserId, "admin:review:list");
        boolean canRisk = hasPerm(operatorUserId, "admin:risk-event:list");
        long totalUsers = sysUserMapper.selectCountByQuery(QueryWrapper.create());
        long dau = countDistinctLoginUsersSince(daysAgo(1));
        long wau = countDistinctLoginUsersSince(daysAgo(7));
        long mau = countDistinctLoginUsersSince(daysAgo(30));
        long onlineDevices = adminStatisticsService.countOnlineDevices();
        long pendingFeedback = canFeedback ? feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending")) : 0L;
        long overdueFeedback = canFeedback ? adminFeedbackService.countOverdue() : 0L;
        long pendingReviews = canReview ? adminReviewService.countPending() : 0L;
        long overdueReviews = canReview ? adminReviewService.countOverdue() : 0L;
        long pendingReports = canReview ? adminReviewService.countPendingBySource(SysReviewTask.SOURCE_REPORT) : 0L;

        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        long todaySensitiveHits = canRisk ? adminRiskEventService.countSinceByType(
                SysRiskEvent.TYPE_SENSITIVE_WORD_MATCH, todayStart) : 0L;
        long todayRiskBlocks = canRisk ? adminRiskEventService.countSinceByType(
                SysRiskEvent.TYPE_RATE_LIMIT, todayStart) : 0L;

        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);
        long riskEvents = canRisk ? adminRiskEventService.countSince(day.getTime()) : 0L;

        return AdminDashboardSummaryVO.builder()
                .totalUsers(totalUsers)
                .activeUsers(wau)
                .dau(dau)
                .wau(wau)
                .mau(mau)
                .onlineDevices(onlineDevices)
                .pendingFeedback(pendingFeedback)
                .overdueFeedback(overdueFeedback)
                .pendingReviews(pendingReviews)
                .overdueReviews(overdueReviews)
                .pendingReports(pendingReports)
                .todaySensitiveHits(todaySensitiveHits)
                .todayRiskBlocks(todayRiskBlocks)
                .riskEvents(riskEvents)
                .build();
    }

    @Override
    public AdminTrendVO trends(int days) {
        return adminStatisticsService.dashboardTrends(days);
    }

    @Override
    public AdminDashboardRealtimeVO realtime(Long operatorUserId) {
        boolean canRisk = hasPerm(operatorUserId, "admin:risk-event:list");
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);

        long todayNewUsers = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getCreateTime).ge(todayStart));
        long todayMessages = imMessageMapper.selectCountByQuery(
                QueryWrapper.create().where(ImMessage::getCreateTime).ge(todayStart));
        long todayLogins = sysLoginAuditMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysLoginAudit::getCreateTime).ge(todayStart)
                        .and(SysLoginAudit::getSuccess).eq(1));

        return AdminDashboardRealtimeVO.builder()
                .onlineDevices(adminStatisticsService.countOnlineDevices())
                .todayNewUsers(todayNewUsers)
                .todayMessages(todayMessages)
                .todayLogins(todayLogins)
                .riskEvents24h(canRisk ? adminRiskEventService.countSince(day.getTime()) : 0L)
                .build();
    }

    @Override
    public List<AdminPendingTaskVO> pendingTasks(Long operatorUserId) {
        boolean canFeedback = hasPerm(operatorUserId, "admin:feedback:list");
        boolean canReview = hasPerm(operatorUserId, "admin:review:list");
        boolean canRisk = hasPerm(operatorUserId, "admin:risk-event:list");

        long pendingFeedback = canFeedback ? feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending")) : 0L;
        long overdueFeedback = canFeedback ? adminFeedbackService.countOverdue() : 0L;
        long overdueReviews = canReview ? adminReviewService.countOverdue() : 0L;
        long pendingReports = canReview ? adminReviewService.countPendingBySource(SysReviewTask.SOURCE_REPORT) : 0L;
        long pendingReviews = canReview ? adminReviewService.countPending() : 0L;
        long pendingOtherReviews = Math.max(0, pendingReviews - pendingReports);
        long riskEvents = canRisk ? adminRiskEventService.countPending() : 0L;

        List<AdminPendingTaskVO> tasks = new ArrayList<>();
        if (overdueFeedback > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("feedback_overdue")
                    .title("overdueFeedback")
                    .count(overdueFeedback)
                    .path("/admin/feedback?overdueOnly=1")
                    .build());
        }
        if (overdueReviews > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("review_overdue")
                    .title("overdueReviews")
                    .count(overdueReviews)
                    .path("/admin/reviews?overdueOnly=1")
                    .build());
        }
        if (pendingFeedback > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("feedback")
                    .title("pendingFeedback")
                    .count(pendingFeedback)
                    .path("/admin/feedback")
                    .build());
        }
        if (pendingReports > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("report")
                    .title("pendingReports")
                    .count(pendingReports)
                    .path("/admin/reports")
                    .build());
        }
        if (pendingOtherReviews > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("review")
                    .title("pendingReviews")
                    .count(pendingOtherReviews)
                    .path("/admin/reviews")
                    .build());
        }
        if (riskEvents > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("risk")
                    .title("riskEvents")
                    .count(riskEvents)
                    .path("/admin/risk-events")
                    .build());
        }
        return tasks;
    }

    private boolean hasPerm(Long userId, String permissionCode) {
        if (userId == null) {
            return true;
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return rbacService.hasPermission(userId, permissionCode)
                || rbacService.hasPermission(userId, RbacConstants.PERM_ALL);
    }

    private Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return cal.getTime();
    }

    /** 成功登录用户去重（按 userId）。 */
    private long countDistinctLoginUsersSince(Date since) {
        List<SysLoginAudit> rows = sysLoginAuditMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(SysLoginAudit::getUserId)
                        .where(SysLoginAudit::getSuccess).eq(1)
                        .and(SysLoginAudit::getCreateTime).ge(since)
                        .and(SysLoginAudit::getUserId).isNotNull());
        return rows.stream()
                .map(SysLoginAudit::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
    }
}
