package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MessageNotificationController 消息通知控制器集成测试
 */
@DisplayName("MessageNotificationController 消息通知控制器集成测试")
class MessageNotificationControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /notifications 获取通知列表测试")
    class ListNotificationsTests {

        @Test
        @DisplayName("获取通知列表应成功")
        void listNotifications_success() throws Exception {
            TestUser user = registerAndLogin("notifuser");

            mockMvc.perform(get("/notifications")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录获取通知列表应返回401")
        void listNotifications_unauthorized() throws Exception {
            mockMvc.perform(get("/notifications"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
