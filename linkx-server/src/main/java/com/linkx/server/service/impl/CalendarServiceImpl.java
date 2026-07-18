package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveCalendarEventDTO;
import com.linkx.server.controller.vo.CalendarEventVO;
import com.linkx.server.entity.CalendarEvent;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.CalendarEventMapper;
import com.linkx.server.service.CalendarService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 日历服务实现
 */
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final CalendarEventMapper calendarEventMapper;

    @Override
    public List<CalendarEventVO> list(Long userId) {
        List<CalendarEvent> events = calendarEventMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .orderBy("date", true)
                        .orderBy("time", true)
        );
        return events.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<CalendarEventVO> listByDate(Long userId, String date) {
        List<CalendarEvent> events = calendarEventMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .eq("date", date)
                        .orderBy("time", true)
        );
        return events.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public CalendarEventVO get(Long userId, Long eventId) {
        CalendarEvent event = calendarEventMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", eventId)
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (event == null) {
            throw new CustomException(404, "事件不存在或无权访问");
        }
        return toVO(event);
    }

    @Override
    @Transactional
    public CalendarEventVO create(Long userId, SaveCalendarEventDTO dto) {
        CalendarEvent event = CalendarEvent.builder()
                .userId(userId)
                .title(dto.getTitle())
                .date(dto.getDate())
                .time(dto.getTime())
                .color(dto.getColor() != null ? dto.getColor() : "var(--lx-accent)")
                .build();
        calendarEventMapper.insert(event);
        return toVO(event);
    }

    @Override
    @Transactional
    public CalendarEventVO update(Long userId, Long eventId, SaveCalendarEventDTO dto) {
        CalendarEvent event = calendarEventMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", eventId)
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (event == null) {
            throw new CustomException(404, "事件不存在或无权修改");
        }
        event.setTitle(dto.getTitle());
        event.setDate(dto.getDate());
        event.setTime(dto.getTime());
        if (dto.getColor() != null) {
            event.setColor(dto.getColor());
        }
        calendarEventMapper.update(event);
        // 重新查询以获取更新后的数据（包括 updateTime）
        CalendarEvent updated = calendarEventMapper.selectOneById(eventId);
        return toVO(updated);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long eventId) {
        CalendarEvent event = calendarEventMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", eventId)
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (event == null) {
            throw new CustomException(404, "事件不存在或无权删除");
        }
        calendarEventMapper.deleteById(eventId);
    }

    private CalendarEventVO toVO(CalendarEvent event) {
        return CalendarEventVO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .date(event.getDate())
                .time(event.getTime())
                .color(event.getColor())
                .createTime(event.getCreateTime())
                .updateTime(event.getUpdateTime())
                .build();
    }
}
