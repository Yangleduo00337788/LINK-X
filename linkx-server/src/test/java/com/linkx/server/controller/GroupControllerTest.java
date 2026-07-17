package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GroupController 群聊控制器集成测试
 */
@DisplayName("GroupController 群聊控制器集成测试")
class GroupControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /group 创建群聊测试")
    class CreateGroupTests {

        @Test
        @DisplayName("未登录创建群聊应返回401")
        void createGroup_unauthorized() throws Exception {
            String body = """
                {
                    "name": "测试群聊",
                    "memberIds": [1]
                }
                """;

            mockMvc.perform(post("/group")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /group/list 获取群聊列表测试")
    class ListGroupsTests {

        @Test
        @DisplayName("获取群聊列表应成功")
        void listGroups_success() throws Exception {
            TestUser user = registerAndLogin("nogroups");

            mockMvc.perform(get("/group/list")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
