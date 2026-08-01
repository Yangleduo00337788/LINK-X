package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端角色权限绑定")
class AdminRolePermissionIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final long SECURITY_ROLE = 1005L;
    /** admin:dashboard:view — 仪表盘在 H2 冒烟中可用 */
    private static final long DASHBOARD_PERM = 2101L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("调整角色权限后接口鉴权随之生效，并可恢复")
    void assignPermissionsGatesApiAccess() throws Exception {
        TestUser admin = registerAndLogin("rpadm");
        grantRole(admin.userId, ADMIN_ROLE);
        admin = login(admin.username, PASSWORD);

        MvcResult before = mockMvc.perform(get("/admin/roles/{id}/permissions", SECURITY_ROLE)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<Long> original = readIds(before);
        assertTrue(original.contains(DASHBOARD_PERM));

        TestUser security = registerAndLogin("rpsec");
        grantRole(security.userId, SECURITY_ROLE);
        security = login(security.username, PASSWORD);
        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", security.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        List<Long> withoutDashboard = new ArrayList<>(original);
        withoutDashboard.remove(DASHBOARD_PERM);
        mockMvc.perform(put("/admin/roles/{id}/permissions", SECURITY_ROLE)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":" + withoutDashboard + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        security = login(security.username, PASSWORD);
        assertFalse(rbacService.getUserPermissionCodes(security.userId).contains("admin:dashboard:view"));
        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", security.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(put("/admin/roles/{id}/permissions", SECURITY_ROLE)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":" + original + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        security = login(security.username, PASSWORD);
        assertTrue(rbacService.getUserPermissionCodes(security.userId).contains("admin:dashboard:view"));
        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", security.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private List<Long> readIds(MvcResult result) throws Exception {
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<Long> ids = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode n : items) {
                ids.add(n.asLong());
            }
        }
        return ids;
    }

    private void grantRole(long userId, long roleId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
