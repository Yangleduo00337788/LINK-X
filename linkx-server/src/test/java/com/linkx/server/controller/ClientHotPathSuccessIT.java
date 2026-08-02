package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户端热路径读接口成功冒烟：注册登录后一次性验证核心列表/配置接口。
 */
@DisplayName("客户端热路径成功冒烟")
class ClientHotPathSuccessIT extends BaseIntegrationTest {

    @Test
    @DisplayName("注册登录后核心读接口均返回 HTTP 200 且 code=200")
    void hotPathEndpoints_returnOk() throws Exception {
        TestUser user = registerAndLogin("hotpath");

        mockMvc.perform(get("/user/me")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/friend/list")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/group/list")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/chat/sessions")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/auth/config")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.registerEnabled").exists());
    }
}
