package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CalendarController 日历控制器集成测试
 */
@DisplayName("CalendarController 日历控制器集成测试")
class CalendarControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /calendar 创建日历事件测试")
    class CreateEventTests {

        @Test
        @DisplayName("创建日历事件应成功")
        void createEvent_success() throws Exception {
            TestUser user = registerAndLogin("caluser");

            String body = """
                {
                    "title": "会议",
                    "date": "2026-07-20",
                    "time": "14:00",
                    "color": "#FF5722"
                }
                """;

            mockMvc.perform(post("/calendar")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("会议"))
                    .andExpect(jsonPath("$.data.date").value("2026-07-20"));
        }

        @Test
        @DisplayName("未登录创建日历事件应返回401")
        void createEvent_unauthorized() throws Exception {
            String body = """
                {
                    "title": "会议",
                    "date": "2026-07-20"
                }
                """;

            mockMvc.perform(post("/calendar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /calendar 获取日历事件列表测试")
    class ListEventsTests {

        @Test
        @DisplayName("获取日历事件列表应成功")
        void listEvents_success() throws Exception {
            TestUser user = registerAndLogin("listcal");

            mockMvc.perform(get("/calendar")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
