package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户偏好设置接口集成测试
 * <p>
 * 覆盖场景：
 * - 未登录访问应 401；
 * - 已登录首次 GET 应返回默认值（不落库）；
 * - PUT 单字段后 GET 应回显该字段；
 * - PUT 时 null 字段不应清空既有值（PUT 语义）。
 * </p>
 */
@DisplayName("用户偏好设置接口测试")
@TestPropertySource(properties = {
        "linkx.app.version=1.0.0"
})
class UserPreferenceControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /user/preference")
    class GetPreferenceTests {

        @Test
        @DisplayName("未登录应返回 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/user/preference"))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("首次获取应返回默认值")
        void get_default() throws Exception {
            TestUser user = registerAndLogin("pref1");
            mockMvc.perform(get("/user/preference")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.autoStart").value(false))
                    .andExpect(jsonPath("$.data.soundNotify").value(true))
                    .andExpect(jsonPath("$.data.messageDetail").value(true))
                    .andExpect(jsonPath("$.data.notifyAtMe").value(true))
                    .andExpect(jsonPath("$.data.notifySound").value(false))
                    .andExpect(jsonPath("$.data.privacyVerifyFriend").value(true))
                    .andExpect(jsonPath("$.data.privacyAllowStranger").value(false))
                    .andExpect(jsonPath("$.data.privacyShowOnline").value(true))
                    .andExpect(jsonPath("$.data.language").value("zh-CN"))
                    .andExpect(jsonPath("$.data.chatBackground").value("default"))
                    .andExpect(jsonPath("$.data.notifyTone").value("default"));
        }
    }

    @Nested
    @DisplayName("PUT /user/preference")
    class UpdatePreferenceTests {

        @Test
        @DisplayName("未登录应返回 401")
        void unauthorized() throws Exception {
            mockMvc.perform(put("/user/preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("部分字段更新后 GET 应反映新值，其它字段保持默认")
        void partial_update() throws Exception {
            TestUser user = registerAndLogin("pref2");

            String body = """
                {
                    "autoStart": true,
                    "soundNotify": false,
                    "chatBackground": "purple"
                }
                """;
            mockMvc.perform(put("/user/preference")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.autoStart").value(true))
                    .andExpect(jsonPath("$.data.soundNotify").value(false))
                    .andExpect(jsonPath("$.data.chatBackground").value("purple"))
                    // 未传入的字段保持默认值（不是被重置为 null）
                    .andExpect(jsonPath("$.data.messageDetail").value(true))
                    .andExpect(jsonPath("$.data.notifyAtMe").value(true))
                    .andExpect(jsonPath("$.data.privacyVerifyFriend").value(true))
                    .andExpect(jsonPath("$.data.language").value("zh-CN"));

            // 再次 GET 应回显一致
            mockMvc.perform(get("/user/preference")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.autoStart").value(true))
                    .andExpect(jsonPath("$.data.soundNotify").value(false))
                    .andExpect(jsonPath("$.data.chatBackground").value("purple"));
        }

        @Test
        @DisplayName("第二次 PUT 不应清空第一次设置的值")
        void incremental_update() throws Exception {
            TestUser user = registerAndLogin("pref3");

            // 第一次：开启 autoStart
            mockMvc.perform(put("/user/preference")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"autoStart\": true}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.autoStart").value(true));

            // 第二次：仅修改 notifySound；autoStart 必须保留为 true
            mockMvc.perform(put("/user/preference")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"notifySound\": true}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.autoStart").value(true))
                    .andExpect(jsonPath("$.data.notifySound").value(true));
        }
    }
}