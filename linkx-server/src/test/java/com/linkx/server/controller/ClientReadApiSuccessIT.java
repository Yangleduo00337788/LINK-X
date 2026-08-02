package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户端只读 API 成功路径批测：扩大 B 矩阵与行覆盖。
 * 断言 HTTP 200 + 业务 code=200。
 */
@DisplayName("客户端只读 API 成功路径批测")
class ClientReadApiSuccessIT extends BaseIntegrationTest {

    private static final String[] PUBLIC_PATHS = {
            "/auth/config",
            "/app/version",
            "/health",
            "/health/live",
    };

    private static final String[] AUTH_READ_PATHS = {
            "/user/me",
            "/user/preference",
            "/friend/list",
            "/friend/requests/incoming",
            "/friend/requests/outgoing",
            "/group/list",
            "/chat/sessions",
            "/notifications",
            "/notifications/mine",
            "/notifications/unread-count",
            "/favorites",
            "/notes",
            "/moments",
            "/calendar",
            "/cloud/storage",
            "/cloud/items",
    };

    @Test
    @DisplayName("公开只读接口应成功")
    void publicReadApis_returnOk() throws Exception {
        for (String path : PUBLIC_PATHS) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    @DisplayName("登录后批量 GET 核心只读接口应成功")
    void batchGet_coreReadApis_returnOk() throws Exception {
        TestUser user = registerAndLogin("readapi");

        for (String path : AUTH_READ_PATHS) {
            mockMvc.perform(get(path).header("Authorization", user.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
