package com.linkx.server.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.admin.AdminExportModule;
import com.linkx.server.controller.admin.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminExportCsvBuilder 单元测试")
class AdminExportCsvBuilderTest {

    @Mock private AdminUserService adminUserService;
    @Mock private AdminDeviceService adminDeviceService;
    @Mock private AdminBlacklistService adminBlacklistService;
    @Mock private AdminRiskEventService adminRiskEventService;
    @Mock private AdminReviewService adminReviewService;
    @Mock private AdminFeedbackService adminFeedbackService;
    @Mock private AdminAuditLogService adminAuditLogService;
    @Mock private AdminStatisticsService adminStatisticsService;

    private AdminExportCsvBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AdminExportCsvBuilder(
                new ObjectMapper(),
                adminUserService,
                adminDeviceService,
                adminBlacklistService,
                adminRiskEventService,
                adminReviewService,
                adminFeedbackService,
                adminAuditLogService,
                adminStatisticsService
        );
    }

    @Test
    @DisplayName("USERS 导出应生成含表头 CSV")
    void users_csv() {
        AdminUserListVO u = AdminUserListVO.builder()
                .id(1L)
                .username("u1")
                .nickname("N")
                .email("a@b.c")
                .phone("1")
                .status(1)
                .roles(List.of("user"))
                .createTime(new Date())
                .build();
        when(adminUserService.listForExport(any())).thenReturn(List.of(u));

        AdminExportCsvBuilder.CsvPayload p = builder.build(AdminExportModule.USERS, "{}");
        assertTrue(p.rowCount() >= 1);
        String csv = new String(p.bytes(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("username"));
        assertTrue(csv.contains("u1"));
        assertNotNull(p.fileName());
    }

    @Test
    @DisplayName("DEVICES 导出")
    void devices_csv() {
        AdminDeviceVO d = mock(AdminDeviceVO.class);
        when(d.getId()).thenReturn(1L);
        when(d.getUserId()).thenReturn(2L);
        when(d.getUsername()).thenReturn("u");
        when(d.getDeviceId()).thenReturn("dev");
        when(d.getOnline()).thenReturn(true);
        when(adminDeviceService.listForExport(any())).thenReturn(List.of(d));
        assertTrue(builder.build(AdminExportModule.DEVICES, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("BLACKLIST 导出")
    void blacklist_csv() {
        AdminBlacklistVO b = mock(AdminBlacklistVO.class);
        when(b.getId()).thenReturn(1L);
        when(b.getUserId()).thenReturn(2L);
        when(b.getUsername()).thenReturn("u");
        when(adminBlacklistService.listForExport(any())).thenReturn(List.of(b));
        assertTrue(builder.build(AdminExportModule.BLACKLIST, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("RISK_EVENTS 导出")
    void risk_csv() {
        AdminRiskEventVO r = mock(AdminRiskEventVO.class);
        when(r.getId()).thenReturn(1L);
        when(r.getTitle()).thenReturn("t");
        when(adminRiskEventService.listForExport(any())).thenReturn(List.of(r));
        assertTrue(builder.build(AdminExportModule.RISK_EVENTS, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("REVIEWS 导出")
    void reviews_csv() {
        AdminReviewVO r = mock(AdminReviewVO.class);
        when(r.getId()).thenReturn(1L);
        when(r.getTitle()).thenReturn("t");
        when(adminReviewService.listForExport(any())).thenReturn(List.of(r));
        assertTrue(builder.build(AdminExportModule.REVIEWS, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("FEEDBACK 导出")
    void feedback_csv() {
        AdminFeedbackVO f = mock(AdminFeedbackVO.class);
        when(f.getId()).thenReturn(1L);
        when(f.getContent()).thenReturn("c");
        when(adminFeedbackService.listForExport(any())).thenReturn(List.of(f));
        assertTrue(builder.build(AdminExportModule.FEEDBACK, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("AUDIT_LOGS / LOGIN_LOGS 导出")
    void auditAndLogin_csv() {
        AdminOperationLogVO op = mock(AdminOperationLogVO.class);
        when(op.getId()).thenReturn(1L);
        when(op.getOperationType()).thenReturn("LOGIN");
        when(adminAuditLogService.listAuditLogsForExport(any())).thenReturn(List.of(op));
        assertTrue(builder.build(AdminExportModule.AUDIT_LOGS, "{}").rowCount() >= 1);

        AdminLoginLogVO login = mock(AdminLoginLogVO.class);
        when(login.getId()).thenReturn(1L);
        when(login.getUsername()).thenReturn("u");
        when(login.getSuccess()).thenReturn(1);
        when(adminAuditLogService.listLoginLogsForExport(any())).thenReturn(List.of(login));
        assertTrue(builder.build(AdminExportModule.LOGIN_LOGS, "{}").rowCount() >= 1);
    }

    @Test
    @DisplayName("STATISTICS 导出")
    void statistics_csv() {
        AdminStatisticOverviewVO ov = mock(AdminStatisticOverviewVO.class);
        when(ov.getTotalUsers()).thenReturn(1L);
        when(ov.getActiveUsers()).thenReturn(1L);
        when(ov.getOnlineDevices()).thenReturn(1L);
        when(ov.getPendingFeedback()).thenReturn(0L);
        when(ov.getPendingReviews()).thenReturn(0L);
        when(ov.getRiskEvents()).thenReturn(0L);
        when(ov.getTodayNewUsers()).thenReturn(0L);
        when(ov.getTodayMessages()).thenReturn(0L);
        when(ov.getTodayLogins()).thenReturn(0L);
        when(ov.getTotalMessages()).thenReturn(0L);
        when(ov.getTotalUploads()).thenReturn(0L);
        when(ov.getClosedFeedback()).thenReturn(0L);

        AdminStatisticUserVO users = mock(AdminStatisticUserVO.class);
        when(users.getNewUsersInRange()).thenReturn(0L);
        when(users.getLoginSuccessInRange()).thenReturn(0L);
        when(users.getLoginFailInRange()).thenReturn(0L);

        AdminStatisticContentVO content = mock(AdminStatisticContentVO.class);
        when(content.getMessagesInRange()).thenReturn(0L);
        when(content.getMomentsInRange()).thenReturn(0L);
        when(content.getUploadsInRange()).thenReturn(0L);

        AdminStatisticRiskVO risk = mock(AdminStatisticRiskVO.class);
        when(risk.getSensitiveHitsInRange()).thenReturn(0L);
        when(risk.getMessageStormsInRange()).thenReturn(0L);
        when(risk.getLoginLocksInRange()).thenReturn(0L);
        when(risk.getRateLimitsInRange()).thenReturn(0L);

        AdminStatisticFeedbackVO feedback = mock(AdminStatisticFeedbackVO.class);
        when(feedback.getCreatedInRange()).thenReturn(0L);
        when(feedback.getRepliedInRange()).thenReturn(0L);
        when(feedback.getClosedInRange()).thenReturn(0L);

        when(adminStatisticsService.overview(anyInt())).thenReturn(ov);
        when(adminStatisticsService.users(anyInt())).thenReturn(users);
        when(adminStatisticsService.content(anyInt())).thenReturn(content);
        when(adminStatisticsService.risk(anyInt())).thenReturn(risk);
        when(adminStatisticsService.feedback(anyInt())).thenReturn(feedback);

        AdminExportCsvBuilder.CsvPayload p = builder.build(AdminExportModule.STATISTICS, "{\"days\":30}");
        assertTrue(p.rowCount() >= 1);
        String csv = new String(p.bytes(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("days"));
    }

    @Test
    @DisplayName("空 queryJson 与非法 JSON 可回退")
    void emptyOrBadQuery_ok() {
        when(adminUserService.listForExport(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> builder.build(AdminExportModule.USERS, null));
        assertDoesNotThrow(() -> builder.build(AdminExportModule.USERS, "not-json"));
    }
}
