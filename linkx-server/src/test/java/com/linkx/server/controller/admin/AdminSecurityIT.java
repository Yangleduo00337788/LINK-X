package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端鉴权 / 越权防护集成测试。
 */
@DisplayName("管理端安全防护")
class AdminSecurityIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE_ID = 1001L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("普通用户访问管理端接口应 403")
    void nonAdminCannotAccessAdminApi() throws Exception {
        TestUser user = registerAndLogin("admden");

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("非管理员走管理端登录：403 且响应不含令牌")
    void nonAdminAdminLoginDoesNotIssueToken() throws Exception {
        TestUser user = registerAndLogin("admlgn");
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", user.username, "password", "Test1234abcd"));

        MvcResult result = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("data") == null || root.get("data").isNull());
        String raw = result.getResponse().getContentAsString();
        assertFalse(raw.contains("accessToken"));
    }

    @Test
    @DisplayName("管理员可访问管理端；不能冻封自己或其他管理员；不能通过接口授 admin")
    void adminProtections() throws Exception {
        TestUser admin = registerAndLogin("admok");
        promoteToAdmin(admin.userId);
        // 重新登录以刷新权限缓存后的会话（promote 已 evict 缓存；旧 token 仍可用）
        admin = login(admin.username, "Test1234abcd");

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 禁止自冻
        mockMvc.perform(post("/admin/users/{id}/freeze", admin.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        TestUser peerAdmin = registerAndLogin("admpair");
        promoteToAdmin(peerAdmin.userId);

        // 禁止冻封其他管理员
        mockMvc.perform(post("/admin/users/{id}/ban", peerAdmin.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 普通用户可被冻结
        TestUser victim = registerAndLogin("admvct");
        mockMvc.perform(post("/admin/users/{id}/freeze", victim.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 可重置普通用户密码（空 body → 生成临时密码）
        TestUser resetTarget = registerAndLogin("admrpw");
        mockMvc.perform(post("/admin/users/{id}/reset-password", resetTarget.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.generated").value(true))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());

        // 禁止重置其他管理员密码
        mockMvc.perform(post("/admin/users/{id}/reset-password", peerAdmin.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 接口不可授予 admin
        TestUser target = registerAndLogin("admgrt");
        mockMvc.perform(post("/rbac/user/{userId}/role/{roleId}", target.userId, ADMIN_ROLE_ID)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertFalse(rbacService.getUserRoleCodes(target.userId).contains(RbacConstants.ROLE_ADMIN));
    }

    @Test
    @DisplayName("管理端登录成功后可刷新 me")
    void adminLoginSucceeds() throws Exception {
        TestUser admin = registerAndLogin("admsuc");
        promoteToAdmin(admin.userId);

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", admin.username, "password", "Test1234abcd"));
        MvcResult result = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(admin.username));
    }

    /** 测试夹具：绕过接口保护，直接写库授予 admin。 */
    private void promoteToAdmin(long userId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(ADMIN_ROLE_ID)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
