package com.linkx.server.controller.admin;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端高级筛选参数")
class AdminAdvancedFilterIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("审核 / 操作日志 / 用户列表接受高级筛选参数并返回 200")
    void advancedFiltersAccepted() throws Exception {
        TestUser admin = registerAndLogin("advflt");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", admin.bearer())
                        .param("page", "1")
                        .param("size", "10")
                        .param("sourceType", "report")
                        .param("targetType", "moment")
                        .param("riskLevel", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/admin/audit-logs")
                        .header("Authorization", admin.bearer())
                        .param("page", "1")
                        .param("size", "10")
                        .param("operationType", "UPDATE_SETTINGS")
                        .param("resultStatus", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", admin.bearer())
                        .param("page", "1")
                        .param("size", "10")
                        .param("deptId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
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
