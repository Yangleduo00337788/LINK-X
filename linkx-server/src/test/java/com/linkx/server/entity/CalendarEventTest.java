package com.linkx.server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalendarEvent 实体测试
 */
@DisplayName("CalendarEvent 实体测试")
class CalendarEventTest {

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("使用Builder创建事件应成功")
        void builderCreatesEvent() {
            Date now = new Date();
            CalendarEvent event = CalendarEvent.builder()
                    .id(1L)
                    .userId(100L)
                    .title("会议")
                    .date("2026-07-20")
                    .time("14:00")
                    .color("#FF5722")
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();

            assertNotNull(event);
            assertEquals(1L, event.getId());
            assertEquals(100L, event.getUserId());
            assertEquals("会议", event.getTitle());
            assertEquals("2026-07-20", event.getDate());
            assertEquals("14:00", event.getTime());
            assertEquals("#FF5722", event.getColor());
        }

        @Test
        @DisplayName("date和time字段应正确存储")
        void dateAndTimeFieldsWork() {
            CalendarEvent event = CalendarEvent.builder()
                    .date("2026-12-25")
                    .time("09:30")
                    .build();

            assertEquals("2026-12-25", event.getDate());
            assertEquals("09:30", event.getTime());
        }

        @Test
        @DisplayName("color字段应支持各种颜色格式")
        void colorFieldSupportsFormats() {
            CalendarEvent redEvent = CalendarEvent.builder().color("#FF0000").build();
            assertEquals("#FF0000", redEvent.getColor());

            CalendarEvent greenEvent = CalendarEvent.builder().color("#00FF00").build();
            assertEquals("#00FF00", greenEvent.getColor());

            CalendarEvent blueEvent = CalendarEvent.builder().color("#0000FF").build();
            assertEquals("#0000FF", blueEvent.getColor());
        }
    }

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造器应创建空对象")
        void noArgsConstructorWorks() {
            CalendarEvent event = new CalendarEvent();
            assertNull(event.getId());
            assertNull(event.getTitle());
            assertNull(event.getDate());
        }

        @Test
        @DisplayName("全参构造器应创建完整对象")
        void allArgsConstructorWorks() {
            Date now = new Date();
            CalendarEvent event = new CalendarEvent(1L, 100L, "生日", "2026-07-17", "00:00", "#FF69B4", now, now, 0);

            assertEquals(1L, event.getId());
            assertEquals(100L, event.getUserId());
            assertEquals("生日", event.getTitle());
            assertEquals("2026-07-17", event.getDate());
            assertEquals("00:00", event.getTime());
            assertEquals("#FF69B4", event.getColor());
            assertEquals(now, event.getCreateTime());
            assertEquals(now, event.getUpdateTime());
            assertEquals(0, event.getDeleted());
        }
    }

    @Nested
    @DisplayName("Getter/Setter测试")
    class GetterSetterTests {

        @Test
        @DisplayName("setter应正确设置值")
        void settersWorkCorrectly() {
            CalendarEvent event = new CalendarEvent();

            event.setId(10L);
            event.setUserId(20L);
            event.setTitle("新事件");
            event.setDate("2026-08-01");
            event.setTime("10:00");
            event.setColor("#4285F4");

            assertEquals(10L, event.getId());
            assertEquals(20L, event.getUserId());
            assertEquals("新事件", event.getTitle());
            assertEquals("2026-08-01", event.getDate());
            assertEquals("10:00", event.getTime());
            assertEquals("#4285F4", event.getColor());
        }
    }
}
