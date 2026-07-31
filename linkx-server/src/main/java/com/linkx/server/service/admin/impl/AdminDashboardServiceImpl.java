package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminDashboardRealtimeVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminPendingTaskVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.admin.AdminDashboardService;
import com.linkx.server.service.admin.AdminReviewService;
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

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final SysUserMapper sysUserMapper;
    private final FeedbackMapper feedbackMapper;
    private final ImMessageMapper imMessageMapper;
    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final AdminReviewService adminReviewService;
    private final AdminStatisticsService adminStatisticsService;

    @Override
    public AdminDashboardSummaryVO summary() {
        long totalUsers = sysUserMapper.selectCountByQuery(QueryWrapper.create());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date weekAgo = cal.getTime();
        long activeUsers = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getUpdateTime).ge(weekAgo));
        long onlineDevices = adminStatisticsService.countOnlineDevices();
        long pendingFeedback = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending"));
        long pendingReviews = adminReviewService.countPending();

        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);
        long riskEvents = adminStatisticsService.countRiskEventsSince(day.getTime());

        return AdminDashboardSummaryVO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineDevices(onlineDevices)
                .pendingFeedback(pendingFeedback)
                .pendingReviews(pendingReviews)
                .riskEvents(riskEvents)
                .build();
    }

    @Override
    public AdminTrendVO trends(int days) {
        return adminStatisticsService.dashboardTrends(days);
    }

    @Override
    public AdminDashboardRealtimeVO realtime() {
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
                .riskEvents24h(adminStatisticsService.countRiskEventsSince(day.getTime()))
                .build();
    }

    @Override
    public List<AdminPendingTaskVO> pendingTasks() {
        long pendingFeedback = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending"));
        long pendingReviews = adminReviewService.countPending();
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);
        long riskEvents = adminStatisticsService.countRiskEventsSince(day.getTime());

        List<AdminPendingTaskVO> tasks = new ArrayList<>();
        if (pendingFeedback > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("feedback")
                    .title("pendingFeedback")
                    .count(pendingFeedback)
                    .path("/admin/feedback")
                    .build());
        }
        if (pendingReviews > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("review")
                    .title("pendingReviews")
                    .count(pendingReviews)
                    .path("/admin/reviews")
                    .build());
        }
        if (riskEvents > 0) {
            tasks.add(AdminPendingTaskVO.builder()
                    .type("risk")
                    .title("riskEvents")
                    .count(riskEvents)
                    .path("/admin/audit-logs")
                    .build());
        }
        return tasks;
    }
}
