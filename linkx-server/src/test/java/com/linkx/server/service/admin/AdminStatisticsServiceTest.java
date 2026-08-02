package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.vo.AdminActivityHeatmapVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticGroupVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.service.admin.impl.AdminStatisticsServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminStatisticsService 统计")
class AdminStatisticsServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock com.linkx.server.mapper.SysUserMapper sysUserMapper;
    @Mock com.linkx.server.mapper.DeviceSessionMapper deviceSessionMapper;
    @Mock com.linkx.server.mapper.FeedbackMapper feedbackMapper;
    @Mock com.linkx.server.mapper.SysLoginAuditMapper sysLoginAuditMapper;
    @Mock com.linkx.server.mapper.ImMessageMapper imMessageMapper;
    @Mock com.linkx.server.mapper.CloudFileMapper cloudFileMapper;
    @Mock com.linkx.server.mapper.admin.SysReviewTaskMapper sysReviewTaskMapper;
    @Mock com.linkx.server.mapper.admin.SysRiskEventMapper sysRiskEventMapper;
    @Mock AdminReviewService adminReviewService;

    private AdminStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsServiceImpl(
                jdbcTemplate, sysUserMapper, deviceSessionMapper, feedbackMapper,
                sysLoginAuditMapper, imMessageMapper, cloudFileMapper, sysReviewTaskMapper,
                sysRiskEventMapper, adminReviewService);
    }

    private void stubDailyCounts() {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getDate("d")).thenReturn(java.sql.Date.valueOf(LocalDate.now()));
            when(rs.getLong("c")).thenReturn(3L);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any());
    }

    @Test
    @DisplayName("概览指标")
    void overview() {
        when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(100L, 80L, 5L);
        when(deviceSessionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(12L);
        when(feedbackMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L, 7L);
        when(adminReviewService.countPending()).thenReturn(4L);
        when(sysRiskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
        when(imMessageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(50L, 1000L);
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(20L);

        AdminStatisticOverviewVO vo = service.overview(14);
        assertEquals(100L, vo.getTotalUsers());
        assertEquals(80L, vo.getActiveUsers());
        assertEquals(12L, vo.getOnlineDevices());
        assertEquals(3L, vo.getPendingFeedback());
        assertEquals(4L, vo.getPendingReviews());
        assertEquals(2L, vo.getRiskEvents());
        assertEquals(5L, vo.getTodayNewUsers());
        assertEquals(50L, vo.getTodayMessages());
        assertEquals(20L, vo.getTodayLogins());
        assertEquals(1000L, vo.getTotalMessages());
        assertEquals(7L, vo.getClosedFeedback());
    }

    @Test
    @DisplayName("用户统计")
    void users() {
        stubDailyCounts();
        when(sysUserMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(90L, 10L);

        AdminStatisticUserVO vo = service.users(7);
        assertNotNull(vo.getTrend());
        assertEquals(2, vo.getStatusBreakdown().size());
        assertTrue(vo.getNewUsersInRange() > 0);
        assertEquals(90L, vo.getStatusBreakdown().get(0).getValue());
    }

    @Test
    @DisplayName("内容统计")
    void content() {
        stubDailyCounts();
        AdminStatisticContentVO vo = service.content(0);
        assertNotNull(vo.getTrend());
        assertEquals(14, vo.getTrend().getLabels().size());
        assertTrue(vo.getMessagesInRange() >= 0);
    }

    @Test
    @DisplayName("风险与审核统计")
    void risk() {
        stubDailyCounts();
        when(sysReviewTaskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(sysReviewTaskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysReviewTask.builder()
                        .createTime(new Date(System.currentTimeMillis() - 3_600_000L))
                        .resolvedAt(new Date())
                        .status(SysReviewTask.STATUS_APPROVED)
                        .build()
        ));

        AdminStatisticRiskVO vo = service.risk(100);
        assertNotNull(vo.getTrend());
        assertEquals(3, vo.getReviewStatusBreakdown().size());
        assertNotNull(vo.getAvgHandleMinutesInRange());
        assertEquals(1L, vo.getPendingReviews());
    }

    @Test
    @DisplayName("反馈统计")
    void feedback() {
        stubDailyCounts();
        when(feedbackMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L, 2L, 3L);

        AdminStatisticFeedbackVO vo = service.feedback(14);
        assertEquals(3, vo.getStatusBreakdown().size());
        assertEquals(3L, vo.getClosedInRange());
    }

    @Test
    @DisplayName("群组统计")
    void groups() {
        stubDailyCounts();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(10L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any()))
                .thenReturn(5L);
        lenient().doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(1L);
            when(rs.getString("name")).thenReturn("Team");
            when(rs.getLong("msg_cnt")).thenReturn(20L);
            when(rs.getLong("member_cnt")).thenReturn(8L);
            when(rs.getTimestamp("last_msg")).thenReturn(new Timestamp(System.currentTimeMillis()));
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(), any());

        AdminStatisticGroupVO vo = service.groups(14);
        assertEquals(10L, vo.getTotalGroups());
        assertEquals(5L, vo.getActiveGroupsInRange());
        assertNotNull(vo.getTrend());
        assertEquals(1, vo.getTopGroups().size());
        assertEquals("Team", vo.getTopGroups().get(0).getName());
    }

    @Test
    @DisplayName("活动热力图")
    void activityHeatmap() {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("wd")).thenReturn(1);
            when(rs.getInt("h")).thenReturn(10);
            when(rs.getLong("c")).thenReturn(5L);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("DAYOFWEEK"), any(RowCallbackHandler.class), any());

        AdminActivityHeatmapVO messages = service.activityHeatmap(14, "messages");
        assertEquals("messages", messages.getMetric());
        assertEquals(14, messages.getDays());
        assertEquals(5L, messages.getMaxValue());
        assertEquals(7 * 24, messages.getCells().size());

        AdminActivityHeatmapVO logins = service.activityHeatmap(14, "logins");
        assertEquals("logins", logins.getMetric());
    }

    @Test
    @DisplayName("仪表盘趋势与设备/风险计数")
    void dashboardTrends_and_counts() {
        stubDailyCounts();
        AdminTrendVO trend = service.dashboardTrends(7);
        assertEquals(7, trend.getLabels().size());
        assertEquals(3, trend.getSeries().size());

        when(deviceSessionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(6L);
        assertEquals(6L, service.countOnlineDevices());

        when(sysRiskEventMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(9L);
        Date since = new Date();
        assertEquals(9L, service.countRiskEventsSince(since));
        assertEquals(9L, service.countRiskEventsSince(null));
    }
}
