package com.linkx.server.controller.admin;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端统计分析")
class AdminStatisticsIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("活跃时段热力图返回 7x24 单元格")
    void activityHeatmapReturnsMatrix() throws Exception {
        TestUser admin = registerAndLogin("stathm");
        grantAdmin(admin.userId);
        admin = login(admin.username, "Test1234abcd");

        mockMvc.perform(get("/admin/statistics/activity-heatmap")
                        .param("days", "14")
                        .param("metric", "logins")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.metric").value("logins"))
                .andExpect(jsonPath("$.data.days").value(14))
                .andExpect(jsonPath("$.data.cells", hasSize(168)))
                .andExpect(jsonPath("$.data.maxValue").exists())
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    @DisplayName("活跃时段热力图支持 messages 指标")
    void activityHeatmapMessagesMetric() throws Exception {
        TestUser admin = registerAndLogin("stathmm");
        grantAdmin(admin.userId);
        admin = login(admin.username, "Test1234abcd");

        mockMvc.perform(get("/admin/statistics/activity-heatmap")
                        .param("metric", "messages")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.metric").value("messages"))
                .andExpect(jsonPath("$.data.cells", hasSize(168)));
    }

    private void grantAdmin(long userId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(ADMIN_ROLE)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
