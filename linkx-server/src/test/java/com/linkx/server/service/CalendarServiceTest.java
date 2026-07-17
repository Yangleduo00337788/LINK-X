package com.linkx.server.service;

import com.linkx.server.controller.vo.CalendarEventVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalendarService 日历服务测试
 */
@DisplayName("CalendarService 日历服务测试")
class CalendarServiceTest extends BaseIntegrationTest {

    @Autowired
    private CalendarService calendarService;

    @Nested
    @DisplayName("list 获取用户日历事件测试")
    class ListTests {

        @Test
        @DisplayName("获取用户日历事件应成功")
        void list_success() {
            List<CalendarEventVO> events = calendarService.list(1L);
            assertNotNull(events);
        }
    }

    @Nested
    @DisplayName("listByDate 获取指定日期事件测试")
    class ListByDateTests {

        @Test
        @DisplayName("获取指定日期事件应成功")
        void listByDate_success() {
            List<CalendarEventVO> events = calendarService.listByDate(1L, "2026-07");
            assertNotNull(events);
        }
    }
}
