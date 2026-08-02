package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

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
                    .andExpect(status().isOk())
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

        @Test
        @DisplayName("带设备头登录后设备列表应包含该设备")
        void listDevices_withDeviceHeader() throws Exception {
            String deviceId = "it-device-" + System.nanoTime();
            TestUser user = registerAndLoginWithDevice("devhdr", deviceId);

            mockMvc.perform(get("/user/devices")
                            .header("Authorization", user.bearer())
                            .header("X-Device-Id", deviceId))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(deviceId)).exists());
        }
    }

    @Nested
    @DisplayName("PUT /user/profile 更新资料测试")
    class UpdateProfileTests {

        @Test
        @DisplayName("更新昵称与签名应成功")
        void updateProfile_success() throws Exception {
            TestUser user = registerAndLogin("upprof");

            mockMvc.perform(put("/user/profile")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nickname":"新昵称","signature":"hello world","gender":"男"}
                                    """))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                    .andExpect(jsonPath("$.data.signature").value("hello world"))
                    .andExpect(jsonPath("$.data.gender").value("男"));

            mockMvc.perform(get("/user/me")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.data.nickname").value("新昵称"));
        }

        @Test
        @DisplayName("未登录更新资料应返回401")
        void updateProfile_unauthorized() throws Exception {
            mockMvc.perform(put("/user/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"x\"}"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /user/avatar 上传头像测试")
    class UploadAvatarTests {

        private static final byte[] VALID_PNG = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
        };

        @Test
        @DisplayName("空文件上传应返回400")
        void uploadAvatar_emptyFile() throws Exception {
            TestUser user = registerAndLogin("avtempty");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.png", "image/png", new byte[0]);

            mockMvc.perform(multipart("/user/avatar")
                            .file(file)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("未登录上传头像应返回401")
        void uploadAvatar_unauthorized() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.png", "image/png", VALID_PNG);

            mockMvc.perform(multipart("/user/avatar").file(file))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /user/change-password 修改密码测试")
    class ChangePasswordTests {

        @Test
        @DisplayName("正确旧密码修改后应能用新密码登录")
        void changePassword_success() throws Exception {
            String username = "pwchg" + (System.nanoTime() % 1_000_000_000L);
            if (username.length() > 32) {
                username = username.substring(0, 32);
            }
            String oldPassword = "Test1234abcd";
            String newPassword = "NewPass1234";
            register(username, oldPassword, "改密测试");
            TestUser user = login(username, oldPassword);

            mockMvc.perform(post("/user/change-password")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"oldPassword":"%s","newPassword":"%s"}
                                    """.formatted(oldPassword, newPassword)))
                    .andExpect(jsonPath("$.code").value(200));

            // JWT iat 精度为秒，改密吊销时间与 iat 同秒时新 token 可能被拒；等待 1s 后再登录
            Thread.sleep(1100L);
            TestUser relogin = login(username, newPassword);
            mockMvc.perform(get("/user/me")
                            .header("Authorization", relogin.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value(username));
        }

        @Test
        @DisplayName("错误旧密码应返回400")
        void changePassword_wrongOld() throws Exception {
            TestUser user = registerAndLogin("pwbad");

            mockMvc.perform(post("/user/change-password")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"oldPassword":"WrongPass1","newPassword":"NewPass1234"}
                                    """))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("GET/PUT /user/preference 偏好与隐私设置测试")
    class PreferenceTests {

        @Test
        @DisplayName("更新隐私设置后 GET 应回显")
        void updatePrivacySettings_success() throws Exception {
            TestUser user = registerAndLogin("privset");

            mockMvc.perform(put("/user/preference")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "privacyVerifyFriend": false,
                                      "privacyAllowStranger": true,
                                      "privacyShowOnline": false
                                    }
                                    """))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.privacyVerifyFriend").value(false))
                    .andExpect(jsonPath("$.data.privacyAllowStranger").value(true))
                    .andExpect(jsonPath("$.data.privacyShowOnline").value(false));

            mockMvc.perform(get("/user/preference")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.privacyVerifyFriend").value(false))
                    .andExpect(jsonPath("$.data.privacyAllowStranger").value(true))
                    .andExpect(jsonPath("$.data.privacyShowOnline").value(false));
        }

        @Test
        @DisplayName("未登录获取偏好应返回401")
        void getPreference_unauthorized() throws Exception {
            mockMvc.perform(get("/user/preference"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
