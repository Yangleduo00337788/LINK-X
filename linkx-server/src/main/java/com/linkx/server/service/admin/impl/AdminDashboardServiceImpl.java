package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.admin.AdminDashboardService;
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
        return AdminDashboardSummaryVO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineDevices(onlineDevices)
                .pendingFeedback(pendingFeedback)
                .pendingReviews(0)
                .riskEvents(0)
                .build();
    }
}
