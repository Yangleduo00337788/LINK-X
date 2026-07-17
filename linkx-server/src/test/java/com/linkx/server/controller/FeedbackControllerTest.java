package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FeedbackController 反馈控制器集成测试
 */
@DisplayName("FeedbackController 反馈控制器集成测试")
class FeedbackControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /feedback 提交反馈测试")
    class CreateFeedbackTests {

        @Test
        @DisplayName("提交反馈应成功")
        void createFeedback_success() throws Exception {
            TestUser user = registerAndLogin("feedbackuser");

            String body = """
                {
                    "type": "bug",
                    "content": "发现一个问题",
                    "contact": "test@example.com"
                }
                """;

            mockMvc.perform(post("/feedback")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("未登录提交反馈应返回401")
        void createFeedback_unauthorized() throws Exception {
            String body = """
                {
                    "type": "bug",
                    "content": "发现问题"
                }
                """;

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /feedback 获取反馈列表测试")
    class ListFeedbackTests {

        @Test
        @DisplayName("获取反馈列表应成功")
        void listFeedback_success() throws Exception {
            TestUser user = registerAndLogin("listfb");

            mockMvc.perform(get("/feedback")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
