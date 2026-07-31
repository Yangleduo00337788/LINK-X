package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 按角色冒烟：对齐管理端开发文档 §37.4。
 * <p>
 * 覆盖运营 / 审核 / 安全 / 只读四类角色的登录、菜单裁剪、权限码与写操作 403。
 * 列表类业务查询在 H2 上存在 MySQL 方言差异，此处不作为冒烟断言点。
 */
@DisplayName("管理端角色冒烟")
class AdminRoleSmokeIT extends BaseIntegrationTest {

    private static final long ROLE_OPS = 1003L;
    private static final long ROLE_AUDIT = 1004L;
    private static final long ROLE_SECURITY = 1005L;
    private static final long ROLE_READONLY = 1006L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("运营管理员：可登录、见运营菜单；不可改配置 / 冻用户")
    void opsAdminMenusAndDeniedWrites() throws Exception {
        TestUser ops = promoteAndRelogin("ops", ROLE_OPS, AdminConstants.ROLE_OPS_ADMIN);

        Set<String> menuNames = menuNames(ops);
        assertTrue(menuNames.contains("dashboard"));
        assertTrue(menuNames.contains("feedback"));
        assertTrue(menuNames.contains("notices"));
        assertTrue(menuNames.contains("statistics"));
        assertFalse(menuNames.contains("settings"));
        assertFalse(menuNames.contains("risk-event"));
        assertFalse(menuNames.contains("devices"));

        Set<String> perms = permissions(ops);
        assertTrue(perms.contains("admin:dashboard:view"));
        assertTrue(perms.contains("admin:feedback:reply"));
        assertTrue(perms.contains("admin:notice:create"));
        assertFalse(perms.contains("admin:setting:edit"));
        assertFalse(perms.contains("admin:user:freeze"));
        assertFalse(perms.contains("admin:risk-event:handle"));

        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", ops.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        TestUser victim = registerAndLogin("opsvct");
        mockMvc.perform(post("/admin/users/{id}/freeze", victim.userId)
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("审核管理员：可看审核/风险；不可新建公告")
    void auditAdminMenusAndDeniedNoticeCreate() throws Exception {
        TestUser audit = promoteAndRelogin("aud", ROLE_AUDIT, AdminConstants.ROLE_AUDIT_ADMIN);

        Set<String> menuNames = menuNames(audit);
        assertTrue(menuNames.contains("review-task") || menuNames.contains("review"));
        assertTrue(menuNames.contains("risk-event"));
        assertTrue(menuNames.contains("devices"));
        assertFalse(menuNames.contains("notices"));
        assertFalse(menuNames.contains("statistics"));

        Set<String> perms = permissions(audit);
        assertTrue(perms.contains("admin:review:approve"));
        assertTrue(perms.contains("admin:risk-event:handle"));
        assertTrue(perms.contains("admin:user:freeze"));
        assertFalse(perms.contains("admin:notice:create"));
        assertFalse(perms.contains("admin:setting:edit"));

        mockMvc.perform(post("/admin/notices")
                        .header("Authorization", audit.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","content":"y","targetSide":"admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("安全管理员：可登录风控与设备；不可回复反馈 / 新建公告")
    void securityAdminMenusAndDeniedOpsWrites() throws Exception {
        TestUser security = promoteAndRelogin("sec", ROLE_SECURITY, AdminConstants.ROLE_SECURITY_ADMIN);

        Set<String> menuNames = menuNames(security);
        assertTrue(menuNames.contains("risk-event"));
        assertTrue(menuNames.contains("devices"));
        assertTrue(menuNames.contains("blacklist"));
        assertTrue(menuNames.contains("audit-log") || menuNames.contains("log"));
        assertFalse(menuNames.contains("feedback"));
        assertFalse(menuNames.contains("notices"));

        Set<String> perms = permissions(security);
        assertTrue(perms.contains("admin:risk-event:handle"));
        assertTrue(perms.contains("admin:device:kick"));
        assertTrue(perms.contains("admin:blacklist:list"));
        assertFalse(perms.contains("admin:feedback:reply"));
        assertFalse(perms.contains("admin:notice:create"));

        mockMvc.perform(post("/admin/feedback/1/reply")
                        .header("Authorization", security.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("只读观察员：可看仪表盘/统计；写操作一律 403")
    void readonlyObserverReadOnly() throws Exception {
        TestUser observer = promoteAndRelogin("ro", ROLE_READONLY, AdminConstants.ROLE_READONLY_OBSERVER);

        Set<String> menuNames = menuNames(observer);
        assertTrue(menuNames.contains("dashboard"));
        assertTrue(menuNames.contains("statistics"));
        assertTrue(menuNames.contains("user"));
        assertTrue(menuNames.contains("devices")); // 只读可看设备，不可踢下线
        assertFalse(menuNames.contains("settings"));
        assertFalse(menuNames.contains("blacklist"));
        assertFalse(menuNames.contains("notices"));

        Set<String> perms = permissions(observer);
        assertTrue(perms.contains("admin:dashboard:view"));
        assertTrue(perms.contains("admin:statistics:view"));
        assertTrue(perms.contains("admin:user:list"));
        assertTrue(perms.contains("admin:device:list"));
        assertFalse(perms.contains("admin:device:kick"));
        assertFalse(perms.contains("admin:user:freeze"));
        assertFalse(perms.contains("admin:notice:create"));
        assertFalse(perms.contains("admin:setting:edit"));
        assertFalse(perms.contains("admin:risk-event:handle"));

        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", observer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/users").header("Authorization", observer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        TestUser victim = registerAndLogin("rovct");
        mockMvc.perform(post("/admin/users/{id}/freeze", victim.userId)
                        .header("Authorization", observer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/notices")
                        .header("Authorization", observer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","content":"y","targetSide":"admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理端登录：四类角色均可拿到令牌")
    void portalRolesCanAdminLogin() throws Exception {
        assertAdminLoginWorks(ROLE_OPS, "oplgn");
        assertAdminLoginWorks(ROLE_AUDIT, "aulgn");
        assertAdminLoginWorks(ROLE_SECURITY, "selgn");
        assertAdminLoginWorks(ROLE_READONLY, "rolgn");
    }

    private void assertAdminLoginWorks(long roleId, String prefix) throws Exception {
        TestUser user = registerAndLogin(prefix);
        grantRole(user.userId, roleId);
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", user.username, "password", "Test1234abcd"));
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private TestUser promoteAndRelogin(String prefix, long roleId, String expectedRole) {
        TestUser user = registerAndLogin(prefix);
        grantRole(user.userId, roleId);
        assertTrue(rbacService.getUserRoleCodes(user.userId).contains(expectedRole));
        return login(user.username, "Test1234abcd");
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

    private Set<String> menuNames(TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/auth/menus")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Set<String> names = new HashSet<>();
        collectMenuNames(items, names);
        return names;
    }

    private Set<String> permissions(TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/auth/permissions")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Set<String> perms = new HashSet<>();
        if (items.isArray()) {
            for (JsonNode n : items) {
                perms.add(n.asText());
            }
        }
        return perms;
    }

    private void collectMenuNames(JsonNode nodes, Set<String> out) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if (node.hasNonNull("name")) {
                out.add(node.get("name").asText());
            }
            collectMenuNames(node.path("children"), out);
        }
    }
}
