package com.linkx.server.controller.admin;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端 IP 限流控制台")
class AdminRateLimitIT extends BaseIntegrationTest {

    private static final long ROLE_SECURITY = 1005L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;
    @Autowired
    private RateLimitService rateLimitService;

    @Test
    @DisplayName("安全角色可查看 hits，白名单可绕过限流")
    void listHitsAndWhitelistBypass() throws Exception {
        TestUser security = registerAndLogin("rlsec");
        grantRole(security.userId, ROLE_SECURITY);
        security = login(security.username, PASSWORD);

        mockMvc.perform(get("/admin/rate-limits/hits")
                        .header("Authorization", security.bearer())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        String ip = "203.0.113.88";
        rateLimitService.removeWhitelist(ip);
        assertFalse(rateLimitService.isWhitelisted(ip));

        rateLimitService.addWhitelist(ip);
        assertTrue(rateLimitService.isWhitelisted(ip));

        // 白名单 IP 的 check 不应抛 429
        rateLimitService.check("biz:test-scope:ip:" + ip, 1, 60);

        rateLimitService.removeWhitelist(ip);
        assertFalse(rateLimitService.isWhitelisted(ip));
    }

    @Test
    @DisplayName("无权限用户不可访问限流控制台")
    void denyWithoutPermission() throws Exception {
        TestUser user = registerAndLogin("rldeny");
        mockMvc.perform(get("/admin/rate-limits/hits")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
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
