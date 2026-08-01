package com.linkx.server.controller.admin;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端设备长期封禁与强绑定")
class AdminDeviceSecurityIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("封禁设备后登录失败，解封后可再登录")
    void banBlocksLoginThenUnbanAllows() throws Exception {
        TestUser admin = registerAndLogin("devbanadm");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String deviceId = "ban-device-a";
        TestUser victim = registerAndLoginWithDevice("devbanvic", deviceId);

        mockMvc.perform(post("/admin/devices/{userId}/{deviceId}/ban", victim.userId, deviceId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"it-ban\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/devices")
                        .param("userId", String.valueOf(victim.userId))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.items[0].banned").value(true));

        expectLoginForbidden(victim.username, deviceId);

        mockMvc.perform(post("/admin/devices/{userId}/{deviceId}/unban", victim.userId, deviceId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        login(victim.username, PASSWORD, deviceId);
    }

    @Test
    @DisplayName("开启强绑定后未批准设备无法登录，批准后可登录")
    void bindingBlocksUnapprovedDevice() throws Exception {
        TestUser admin = registerAndLogin("devbindadm");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String approvedDevice = "bind-ok";
        String blockedDevice = "bind-new";
        TestUser victim = registerAndLoginWithDevice("devbindvic", approvedDevice);

        mockMvc.perform(post("/admin/users/{id}/device-binding", victim.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/users/{id}", victim.userId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceBindingEnabled").value(true));

        // 开启时当前会话已自动批准
        login(victim.username, PASSWORD, approvedDevice);

        expectLoginForbidden(victim.username, blockedDevice);

        mockMvc.perform(post("/admin/users/{id}/devices/{deviceId}/approve", victim.userId, blockedDevice)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        login(victim.username, PASSWORD, blockedDevice);

        mockMvc.perform(get("/admin/users/{id}/devices", victim.userId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == '" + blockedDevice + "')].approved").value(true));
    }

    private void expectLoginForbidden(String username, String deviceId) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", username, "password", PASSWORD));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Device-Id", deviceId)
                        .header("X-Device-Name", "JUnit")
                        .header("X-Device-Type", "Test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
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
