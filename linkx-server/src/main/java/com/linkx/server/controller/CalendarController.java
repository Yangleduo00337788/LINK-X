package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SaveCalendarEventDTO;
import com.linkx.server.controller.vo.CalendarEventVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.CalendarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日历控制器
 * 路径中的 eventId 使用字符串接收，再 Long.parseLong，避免雪花 ID 精度问题。
 */
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final JwtUtils jwtUtils;

    /**
     * 获取所有日历事件
     */
    @GetMapping
    public Result<List<CalendarEventVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.list(userId));
    }

    /**
     * 获取指定日期的事件
     */
    @GetMapping("/date/{date}")
    public Result<List<CalendarEventVO>> listByDate(
            @PathVariable String date,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.listByDate(userId, date));
    }

    /**
     * 获取单条事件
     */
    @GetMapping("/{eventId}")
    public Result<CalendarEventVO> get(
            @PathVariable String eventId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.get(userId, parseId(eventId)));
    }

    /**
     * 创建事件
     */
    @PostMapping
    public Result<CalendarEventVO> create(
            @Valid @RequestBody SaveCalendarEventDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.create(userId, dto));
    }

    /**
     * 更新事件
     */
    @PutMapping("/{eventId}")
    public Result<CalendarEventVO> update(
            @PathVariable String eventId,
            @Valid @RequestBody SaveCalendarEventDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.update(userId, parseId(eventId), dto));
    }

    /**
     * 删除事件
     */
    @DeleteMapping("/{eventId}")
    public Result<Void> delete(
            @PathVariable String eventId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        calendarService.delete(userId, parseId(eventId));
        return Result.success(null);
    }

    /**
     * 触发日程提醒：写入消息通知列表（由客户端在提醒时刻调用）
     */
    @PostMapping("/{eventId}/remind")
    public Result<Void> fireReminder(
            @PathVariable String eventId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        calendarService.fireReminder(userId, parseId(eventId));
        return Result.success(null);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "无效的 ID");
        }
    }
}
