package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveCalendarEventDTO;
import com.linkx.server.controller.vo.CalendarEventVO;
import com.linkx.server.entity.CalendarEvent;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.CalendarEventMapper;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CalendarServiceImpl 日历")
class CalendarServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock CalendarEventMapper calendarEventMapper;
    @Mock MessageNotificationService notificationService;
    @Mock ImMessagePushService imPushService;

    private CalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CalendarServiceImpl(calendarEventMapper, notificationService, imPushService);
    }

    private CalendarEvent event(long id) {
        return CalendarEvent.builder()
                .id(id).userId(USER_ID).title("Meeting").date("2026-08-02")
                .time("10:00").color("#f00").createTime(new Date()).build();
    }

    @Nested
    @DisplayName("查询")
    class Query {
        @Test
        @DisplayName("list / listByDate")
        void lists() {
            when(calendarEventMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(event(1L)));
            assertEquals(1, service.list(USER_ID).size());
            assertEquals(1, service.listByDate(USER_ID, "2026-08-02").size());
        }

        @Test
        @DisplayName("get 成功与 404")
        void get() {
            when(calendarEventMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(event(1L));
            assertEquals("Meeting", service.get(USER_ID, 1L).getTitle());
            when(calendarEventMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class, () -> service.get(USER_ID, 9L));
        }
    }

    @Nested
    @DisplayName("写操作")
    class Write {
        @Test
        @DisplayName("create 默认颜色")
        void create() {
            when(calendarEventMapper.insert(any(CalendarEvent.class))).thenAnswer(inv -> {
                CalendarEvent e = inv.getArgument(0);
                e.setId(5L);
                return 1;
            });
            SaveCalendarEventDTO dto = new SaveCalendarEventDTO();
            dto.setTitle("T");
            dto.setDate("2026-08-02");
            dto.setTime("09:00");
            CalendarEventVO vo = service.create(USER_ID, dto);
            assertEquals(5L, vo.getId());
            assertEquals("var(--lx-accent)", vo.getColor());
        }

        @Test
        @DisplayName("update / delete / fireReminder")
        void mutate() {
            CalendarEvent e = event(1L);
            when(calendarEventMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(e);
            when(calendarEventMapper.selectOneById(1L)).thenReturn(e);

            SaveCalendarEventDTO dto = new SaveCalendarEventDTO();
            dto.setTitle("New");
            dto.setDate("2026-08-03");
            dto.setTime("11:00");
            dto.setColor("#0f0");
            assertEquals("New", service.update(USER_ID, 1L, dto).getTitle());

            service.delete(USER_ID, 1L);
            verify(calendarEventMapper).deleteById(1L);

            service.fireReminder(USER_ID, 1L);
            verify(notificationService).create(eq(USER_ID), isNull(), eq("日程提醒"), isNull(),
                    eq("calendar_remind"), eq(1L), contains("New"));
            verify(imPushService).pushToUser(eq(USER_ID), eq("notification_refresh"), anyMap());
        }

        @Test
        @DisplayName("全天提醒文案")
        void allDayReminder() {
            CalendarEvent e = event(2L);
            e.setTime("  ");
            when(calendarEventMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(e);
            service.fireReminder(USER_ID, 2L);
            verify(notificationService).create(anyLong(), any(), anyString(), any(), anyString(), anyLong(),
                    contains("全天"));
        }
    }
}
