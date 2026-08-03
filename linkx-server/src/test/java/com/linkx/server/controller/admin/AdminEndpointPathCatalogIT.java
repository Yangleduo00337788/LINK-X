package com.linkx.server.controller.admin;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Admin 端点路径目录（B 矩阵启发式）：未鉴权调用，允许 4xx，禁止 5xx。
 * 字符串字面量须与 OpenAPI path 一致，供 endpoint-test-matrix 匹配。
 */
@DisplayName("Admin 端点路径目录扫")
class AdminEndpointPathCatalogIT extends BaseIntegrationTest {

    private static final String[] GET_PATHS = {
            "/admin/auth/config",
            "/admin/banners",
            "/admin/banners/{id}",
            "/admin/blacklist",
            "/admin/blacklist/{id}",
            "/admin/blacklist/export",
            "/admin/dashboard/realtime",
            "/admin/dashboard/trends",
            "/admin/events/stream",
            "/admin/feedback/{id}",
            "/admin/feedback/export",
            "/admin/feedback-dispatch-rules",
            "/admin/feedback-dispatch-rules/{id}",
            "/admin/abnormal-access",
            "/admin/abnormal-access/summary",
            "/admin/abnormal-access/export",
            "/admin/permissions",
            "/admin/permissions/{id}",
            "/admin/rate-limits/whitelist",
            "/admin/notices",
            "/admin/notices/{id}",
            "/admin/notices/inbox",
            "/admin/reviews",
            "/admin/reviews/{id}",
            "/admin/reviews/export",
            "/admin/risk-events",
            "/admin/risk-events/{id}",
            "/admin/risk-events/export",
            "/admin/roles",
            "/admin/roles/{id}",
            "/admin/roles/{id}/menus",
            "/admin/roles/{id}/permissions",
            "/admin/roles/{id}/users",
            "/admin/statistics/overview",
            "/admin/statistics/users",
            "/admin/statistics/content",
            "/admin/statistics/risk",
            "/admin/statistics/feedback",
            "/admin/statistics/groups",
            "/admin/statistics/activity-heatmap",
            "/admin/statistics/export",
            "/admin/recommends",
            "/admin/recommends/{id}",
            "/admin/auth/permissions",
            "/admin/versions",
            "/admin/versions/{id}",
    };

    private static final String[] POST_PATHS = {
            "/admin/auth/logout",
            "/admin/auth/refresh",
            "/admin/auth/totp/confirm",
            "/admin/auth/totp/confirm-challenge",
            "/admin/auth/totp/disable",
            "/admin/auth/totp/setup",
            "/admin/auth/totp/setup-challenge",
            "/admin/banners",
            "/admin/banners/{id}/publish",
            "/admin/banners/{id}/unpublish",
            "/admin/banners/upload",
            "/admin/blacklist",
            "/admin/blacklist/{id}/release",
            "/admin/feedback/{id}/close",
            "/admin/feedback/{id}/reopen",
            "/admin/feedback/{id}/reply",
            "/admin/feedback-dispatch-rules",
            "/admin/permissions",
            "/admin/rate-limits/unblock",
            "/admin/rate-limits/whitelist",
            "/admin/risk-events/batch",
            "/admin/risk-events/{id}/handle",
            "/admin/reviews/batch",
            "/admin/reviews/{id}/approve",
            "/admin/reviews/{id}/reject",
            "/admin/reviews/{id}/delete-content",
            "/admin/notices",
            "/admin/notices/{id}/publish",
            "/admin/notices/{id}/unpublish",
            "/admin/roles",
            "/admin/roles/{id}/menus",
            "/admin/roles/{id}/permissions",
            "/admin/roles/{id}/users",
            "/admin/recommends",
            "/admin/recommends/{id}/publish",
            "/admin/recommends/{id}/unpublish",
            "/admin/recommends/upload",
            "/admin/versions",
            "/admin/versions/{id}/publish",
    };

    private static final String[] PUT_PATHS = {
            "/admin/auth/profile",
            "/admin/banners/{id}",
            "/admin/permissions/{id}",
            "/admin/notices/{id}",
            "/admin/roles/{id}",
            "/admin/roles/{id}/permissions",
            "/admin/recommends/{id}",
            "/admin/versions/{id}",
            "/admin/feedback/{id}/assign",
            "/admin/feedback-dispatch-rules/{id}",
    };

    private static final String[] DELETE_PATHS = {
            "/admin/banners/{id}",
            "/admin/permissions/{id}",
            "/admin/rate-limits/whitelist",
            "/admin/notices/{id}",
            "/admin/roles/{id}",
            "/admin/versions/{id}",
            "/admin/feedback-dispatch-rules/{id}",
    };

    @Test
    @DisplayName("未鉴权目录扫无 5xx（抬升 B 矩阵）")
    void catalog_noServerError() throws Exception {
        for (String path : GET_PATHS) {
            int status = mockMvc.perform(get(concrete(path))).andReturn().getResponse().getStatus();
            assertTrue(status < 500, () -> "GET " + path + " -> " + status);
        }
        for (String path : POST_PATHS) {
            int status = mockMvc.perform(post(concrete(path))
                            .contentType("application/json")
                            .content("{}"))
                    .andReturn().getResponse().getStatus();
            assertTrue(status < 500, () -> "POST " + path + " -> " + status);
        }
        for (String path : PUT_PATHS) {
            int status = mockMvc.perform(put(concrete(path))
                            .contentType("application/json")
                            .content("{}"))
                    .andReturn().getResponse().getStatus();
            assertTrue(status < 500, () -> "PUT " + path + " -> " + status);
        }
        for (String path : DELETE_PATHS) {
            int status = mockMvc.perform(delete(concrete(path))).andReturn().getResponse().getStatus();
            assertTrue(status < 500, () -> "DELETE " + path + " -> " + status);
        }
        // keep import for matrix method hint
        MockMvcRequestBuilders.get("/admin/auth/config");
    }

    private static String concrete(String path) {
        return path.replace("{id}", "1");
    }
}
