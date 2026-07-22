package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatController 聊天控制器集成测试
 */
@DisplayName("ChatController 聊天控制器集成测试")
class ChatControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /chat/sessions 获取会话列表测试")
    class ListSessionsTests {

        @Test
        @DisplayName("获取会话列表应成功")
        void listSessions_success() throws Exception {
            TestUser user = registerAndLogin("chatuser");

            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录获取会话列表应返回401")
        void listSessions_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/sessions"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /chat/search 搜索消息")
    class SearchTests {

        @Test
        @DisplayName("搜索应返回数组")
        void search_success() throws Exception {
            TestUser user = registerAndLogin("chatsearch");

            mockMvc.perform(get("/chat/search")
                            .param("q", "hello")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录搜索应返回401")
        void search_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/search").param("q", "hello"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /chat/private/{friendId}")
    class OpenPrivateTests {

        @Test
        @DisplayName("非好友打开私聊应失败")
        void openPrivate_notFriend() throws Exception {
            TestUser a = registerAndLogin("chata");
            TestUser b = registerAndLogin("chatb");

            mockMvc.perform(post("/chat/private/{friendId}", b.userId)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(403),
                            org.hamcrest.Matchers.is(404)
                    )));
        }
    }
}
