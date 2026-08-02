package com.linkx.server.controller.admin;

import com.linkx.server.controller.admin.vo.AdminActivityHeatmapVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticGroupVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.service.admin.AdminStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStatisticsController 单元测试")
class AdminStatisticsControllerTest {

    @Mock AdminStatisticsService adminStatisticsService;

    private AdminStatisticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminStatisticsController(adminStatisticsService);
    }

    @Test
    @DisplayName("overview/users/content/risk/feedback/groups/heatmap 委托 service")
    void endpointsDelegate() {
        when(adminStatisticsService.overview(14)).thenReturn(AdminStatisticOverviewVO.builder().build());
        when(adminStatisticsService.users(14)).thenReturn(AdminStatisticUserVO.builder().build());
        when(adminStatisticsService.content(14)).thenReturn(AdminStatisticContentVO.builder().build());
        when(adminStatisticsService.risk(14)).thenReturn(AdminStatisticRiskVO.builder().build());
        when(adminStatisticsService.feedback(14)).thenReturn(AdminStatisticFeedbackVO.builder().build());
        when(adminStatisticsService.groups(14)).thenReturn(AdminStatisticGroupVO.builder().build());
        when(adminStatisticsService.activityHeatmap(30, "logins"))
                .thenReturn(AdminActivityHeatmapVO.builder().metric("logins").days(30).build());

        assertEquals(200, controller.overview(14).getCode());
        assertEquals(200, controller.users(14).getCode());
        assertEquals(200, controller.content(14).getCode());
        assertEquals(200, controller.risk(14).getCode());
        assertEquals(200, controller.feedback(14).getCode());
        assertEquals(200, controller.groups(14).getCode());
        assertEquals("logins", controller.activityHeatmap(30, "logins").getData().getMetric());

        verify(adminStatisticsService).overview(14);
        verify(adminStatisticsService).activityHeatmap(30, "logins");
    }

    @Test
    @DisplayName("export CSV 汇总多指标")
    void exportCsv() {
        AdminStatisticOverviewVO ov = AdminStatisticOverviewVO.builder()
                .totalUsers(100L).activeUsers(80L).onlineDevices(5L)
                .pendingFeedback(1L).pendingReviews(2L).riskEvents(3L)
                .todayNewUsers(4L).todayMessages(50L).todayLogins(20L)
                .totalMessages(1000L).totalUploads(200L).closedFeedback(7L)
                .build();
        AdminStatisticUserVO users = AdminStatisticUserVO.builder()
                .newUsersInRange(10L).loginSuccessInRange(30L).loginFailInRange(2L).build();
        AdminStatisticContentVO content = AdminStatisticContentVO.builder()
                .messagesInRange(40L).momentsInRange(5L).uploadsInRange(6L).build();
        AdminStatisticRiskVO risk = AdminStatisticRiskVO.builder()
                .sensitiveHitsInRange(1L).messageStormsInRange(2L)
                .loginLocksInRange(3L).rateLimitsInRange(4L).build();
        AdminStatisticFeedbackVO feedback = AdminStatisticFeedbackVO.builder()
                .createdInRange(8L).repliedInRange(9L).closedInRange(10L).build();

        when(adminStatisticsService.overview(14)).thenReturn(ov);
        when(adminStatisticsService.users(14)).thenReturn(users);
        when(adminStatisticsService.content(14)).thenReturn(content);
        when(adminStatisticsService.risk(14)).thenReturn(risk);
        when(adminStatisticsService.feedback(14)).thenReturn(feedback);

        ResponseEntity<byte[]> resp = controller.export(14);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        String csv = new String(resp.getBody());
        assertTrue(csv.contains("totalUsers"));
        assertTrue(csv.contains("messagesInRange"));
        assertTrue(csv.contains("closedFeedbackInRange"));
    }
}
