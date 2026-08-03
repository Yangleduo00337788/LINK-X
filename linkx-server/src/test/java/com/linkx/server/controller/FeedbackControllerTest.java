package com.linkx.server.controller;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * FeedbackController 反馈控制器集成测试
 */
@DisplayName("FeedbackController 反馈控制器集成测试")
class FeedbackControllerTest extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

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

    @Nested
    @DisplayName("反馈多轮回复")
    class FeedbackReplyTests {

        @Test
        @DisplayName("管理员回复与用户追评")
        void adminReplyAndUserFollowUp() throws Exception {
            TestUser user = registerAndLogin("fbreplyuser");
            String body = """
                {
                    "type": "bug",
                    "content": "第一轮反馈内容",
                    "contact": "reply@test.com"
                }
                """;
            String createRes = mockMvc.perform(post("/feedback")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            long feedbackId = objectMapper.readTree(createRes).path("data").path("id").asLong();

            TestUser admin = registerAndLogin("fbreplyadmin");
            grantAdmin(admin.userId);
            admin = login(admin.username, PASSWORD);

            mockMvc.perform(post("/admin/feedback/" + feedbackId + "/reply")
                            .header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"官方第一次回复\"}"))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/admin/feedback/" + feedbackId + "/replies")
                            .header("Authorization", admin.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].senderType").value("admin"));

            mockMvc.perform(post("/feedback/" + feedbackId + "/replies")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"用户补充说明\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.senderType").value("user"));

            mockMvc.perform(get("/feedback/" + feedbackId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.replies.length()").value(2));
        }
    }

    private void grantAdmin(long userId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(ADMIN_ROLE)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
