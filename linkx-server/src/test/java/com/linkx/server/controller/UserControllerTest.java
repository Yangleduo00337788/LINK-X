package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 用户控制器集成测试
 */
@DisplayName("UserController 用户控制器集成测试")
class UserControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /user/me 获取当前用户信息测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("已登录获取用户信息应成功")
        void getCurrentUser_success() throws Exception {
            TestUser user = registerAndLogin("me");

            mockMvc.perform(get("/user/me")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(user.userId))
                    .andExpect(jsonPath("$.data.username").value(user.username));
        }

        @Test
        @DisplayName("未登录获取用户信息应返回401")
        void getCurrentUser_unauthorized() throws Exception {
            mockMvc.perform(get("/user/me"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /user/{userId}/profile 获取用户公开资料测试")
    class GetUserProfileTests {

        @Test
        @DisplayName("获取已存在用户资料应成功")
        void getUserProfile_success() throws Exception {
            TestUser user = registerAndLogin("view");

            mockMvc.perform(get("/user/" + user.userId + "/profile")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(user.userId))
                    .andExpect(jsonPath("$.data.username").value(user.username));
        }
    }

    @Nested
    @DisplayName("GET /user/devices 获取登录设备列表测试")
    class ListDevicesTests {

        @Test
        @DisplayName("获取设备列表应成功")
        void listDevices_success() throws Exception {
            TestUser user = registerAndLogin("devices");

            mockMvc.perform(get("/user/devices")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
