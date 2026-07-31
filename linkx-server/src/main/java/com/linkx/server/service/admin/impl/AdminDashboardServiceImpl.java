package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.admin.AdminDashboardService;
import com.linkx.server.service.admin.AdminReviewService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final SysUserMapper sysUserMapper;
    private final DeviceSessionMapper deviceSessionMapper;
    private final FeedbackMapper feedbackMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final AdminReviewService adminReviewService;

    @Override
    public AdminDashboardSummaryVO summary() {
        long totalUsers = sysUserMapper.selectCountByQuery(QueryWrapper.create());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date weekAgo = cal.getTime();
        long activeUsers = sysUserMapper.selectCountByQuery(
                QueryWrapper.create().where(SysUser::getUpdateTime).ge(weekAgo));
        long onlineDevices = deviceSessionMapper.selectCountByQuery(QueryWrapper.create());
        long pendingFeedback = feedbackMapper.selectCountByQuery(
                QueryWrapper.create().where(Feedback::getStatus).eq("pending"));
        long pendingReviews = adminReviewService.countPending();

        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);
        Date dayAgo = day.getTime();
        long riskEvents = sysAuditLogMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysAuditLog::getCreateTime).ge(dayAgo)
                        .and(SysAuditLog::getOperationType).in(
                                SysAuditLog.OperationType.SENSITIVE_WORD_MATCH.name(),
                                SysAuditLog.OperationType.MESSAGE_STORM.name()));

        return AdminDashboardSummaryVO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineDevices(onlineDevices)
                .pendingFeedback(pendingFeedback)
                .pendingReviews(pendingReviews)
                .riskEvents(riskEvents)
                .build();
    }
}
