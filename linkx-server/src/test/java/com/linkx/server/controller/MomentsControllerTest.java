package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MomentsController 朋友圈控制器集成测试
 */
@DisplayName("MomentsController 朋友圈控制器集成测试")
class MomentsControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /moments 发布动态测试")
    class PublishMomentsTests {

        @Test
        @DisplayName("发布动态应成功")
        void publishMoments_success() throws Exception {
            TestUser user = registerAndLogin("momentsuser");

            String body = """
                {
                    "content": "今天天气真好！"
                }
                """;

            mockMvc.perform(post("/moments")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").value("今天天气真好！"));
        }

        @Test
        @DisplayName("未登录发布动态应返回401")
        void publishMoments_unauthorized() throws Exception {
            String body = """
                {
                    "content": "测试内容"
                }
                """;

            mockMvc.perform(post("/moments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
