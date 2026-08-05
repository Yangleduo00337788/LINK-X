package com.linkx.server.controller.admin;

import com.linkx.server.controller.admin.dto.AdminBiQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBiMetricVO;
import com.linkx.server.controller.admin.vo.AdminBiQueryVO;
import com.linkx.server.controller.admin.vo.AdminBigScreenVO;
import com.linkx.server.service.admin.AdminBiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBiController 单元测试")
class AdminBiControllerTest {

    @Mock AdminBiService adminBiService;

    private AdminBiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminBiController(adminBiService);
    }

    @Test
    @DisplayName("metrics/query/bigScreen 委托 service")
    void endpointsDelegate() {
        when(adminBiService.listMetrics()).thenReturn(List.of(AdminBiMetricVO.builder().key("logins").build()));
        when(adminBiService.query(org.mockito.ArgumentMatchers.any())).thenReturn(AdminBiQueryVO.builder().metric("logins").build());
        when(adminBiService.bigScreenData()).thenReturn(AdminBigScreenVO.builder().dau(10L).build());

        assertEquals(200, controller.metrics().getCode());
        AdminBiQueryDTO dto = new AdminBiQueryDTO();
        dto.setMetric("logins");
        assertEquals(200, controller.query(dto).getCode());
        assertEquals(10L, controller.bigScreenData().getData().getDau());

        verify(adminBiService).listMetrics();
        verify(adminBiService).query(dto);
        verify(adminBiService).bigScreenData();
    }
}
