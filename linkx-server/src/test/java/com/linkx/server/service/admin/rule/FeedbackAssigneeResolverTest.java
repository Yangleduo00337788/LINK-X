package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.entity.admin.SysDutySchedule;
import com.linkx.server.entity.admin.SysDutyScheduleSlot;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleSlotMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.sql.Time;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackAssigneeResolver")
class FeedbackAssigneeResolverTest {

    @Mock SysDutyScheduleMapper dutyScheduleMapper;
    @Mock SysDutyScheduleSlotMapper dutyScheduleSlotMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private FeedbackAssigneeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new FeedbackAssigneeResolver(
                dutyScheduleMapper, dutyScheduleSlotMapper, redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("固定处理人")
    void resolveFixed() {
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .assigneeSource("fixed")
                .assigneeId(42L)
                .build();
        assertEquals(Optional.of(42L), resolver.resolve(rule));
    }

    @Test
    @DisplayName("轮询池")
    void resolveRoundRobin() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(2L);
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .id(9L)
                .assigneeSource("round_robin")
                .actionConfig("{\"assigneeIds\":[10,20,30]}")
                .build();
        assertEquals(Optional.of(20L), resolver.resolve(rule));
    }

    @Test
    @DisplayName("值班表解析")
    void resolveDuty() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        int weekday = now.getDayOfWeek().getValue();
        LocalTime current = now.toLocalTime().withSecond(0).withNano(0);
        LocalTime end = current.plusHours(2);

        SysDutySchedule schedule = SysDutySchedule.builder()
                .id(1L)
                .enabled(true)
                .deleted(0)
                .timezone("Asia/Shanghai")
                .build();
        SysDutyScheduleSlot slot = SysDutyScheduleSlot.builder()
                .scheduleId(1L)
                .weekday(weekday)
                .startTime(Time.valueOf(current))
                .endTime(Time.valueOf(end))
                .assigneeId(77L)
                .deleted(0)
                .build();

        when(dutyScheduleMapper.selectOneById(1L)).thenReturn(schedule);
        when(dutyScheduleSlotMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(slot));

        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .assigneeSource("duty")
                .dutyScheduleId(1L)
                .build();
        assertEquals(Optional.of(77L), resolver.resolve(rule));
    }

    @Test
    @DisplayName("跨午夜时段")
    void isWithinSlotOvernight() {
        LocalTime current = LocalTime.of(1, 0);
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(6, 0);
        assertTrue(FeedbackAssigneeResolver.isWithinSlot(current, start, end));
        assertFalse(FeedbackAssigneeResolver.isWithinSlot(LocalTime.of(12, 0), start, end));
    }

    @Test
    @DisplayName("轮询池解析字符串雪花ID")
    void parseAssigneePool_stringSnowflakeIds() {
        List<Long> pool = resolver.parseAssigneePool(
                "{\"assigneeIds\":[\"442465283856822272\",\"442465283856822273\"]}");
        assertEquals(2, pool.size());
        assertEquals(442465283856822272L, pool.get(0));
        assertEquals(442465283856822273L, pool.get(1));
    }
}
