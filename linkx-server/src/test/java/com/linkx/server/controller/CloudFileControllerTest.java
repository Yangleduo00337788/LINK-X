package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("CloudFileController 文件列表集成测试")
class CloudFileControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("未登录应 401")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/files"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("列出我的文件应成功")
    void listMine_success() throws Exception {
        TestUser user = registerAndLogin("files");
        mockMvc.perform(get("/files").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
