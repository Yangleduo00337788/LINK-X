package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FriendController 好友控制器集成测试
 */
@DisplayName("FriendController 好友控制器集成测试")
class FriendControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /friend/search 搜索用户测试")
    class SearchUsersTests {

        @Test
        @DisplayName("搜索用户应成功")
        void searchUsers_success() throws Exception {
            TestUser user = registerAndLogin("searcher");

            mockMvc.perform(get("/friend/search")
                            .param("keyword", "test")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录搜索应返回401")
        void searchUsers_unauthorized() throws Exception {
            mockMvc.perform(get("/friend/search")
                            .param("keyword", "test"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /friend/list 获取好友列表测试")
    class ListFriendsTests {

        @Test
        @DisplayName("获取好友列表应成功")
        void listFriends_success() throws Exception {
            TestUser user = registerAndLogin("nofriends");

            mockMvc.perform(get("/friend/list")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
