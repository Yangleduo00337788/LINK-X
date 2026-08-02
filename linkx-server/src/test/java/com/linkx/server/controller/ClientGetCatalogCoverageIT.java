package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 客户端 GET 目录扫（仅含 H2 测试库可支撑的路径）：提升行覆盖。
 * 允许业务 4xx，禁止 5xx。
 */
@DisplayName("客户端 GET 目录覆盖扫")
class ClientGetCatalogCoverageIT extends BaseIntegrationTest {

    private static final String[] PATHS = {
            "/app/version",
            "/auth/captcha",
            "/auth/config",
            "/balance",
            "/balance/logs",
            "/calendar",
            "/chat/sessions",
            "/chat/unread-total",
            "/cloud/activities",
            "/cloud/breadcrumb",
            "/cloud/items",
            "/cloud/storage",
            "/conference/active",
            "/favorites",
            "/favorites/storage",
            "/favorites/tags",
            "/feedback",
            "/friend/list",
            "/friend/requests/incoming",
            "/friend/requests/outgoing",
            "/group/invitations",
            "/group/list",
            "/health",
            "/health/live",
            "/health/ready",
            "/moments",
            "/notes",
            "/notifications",
            "/notifications/mine",
            "/notifications/unread",
            "/notifications/unread-count",
            "/rbac/role",
            "/user/devices",
            "/user/me",
            "/user/preference",
    };

    @Test
    @DisplayName("登录后目录 GET 均无 5xx")
    void catalogGets_noServerError() throws Exception {
        TestUser user = registerAndLogin("getcat");
        for (String path : PATHS) {
            int status = mockMvc.perform(get(path).header("Authorization", user.bearer()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertTrue(status > 0 && status < 500, () -> path + " -> " + status);
        }
    }
}
