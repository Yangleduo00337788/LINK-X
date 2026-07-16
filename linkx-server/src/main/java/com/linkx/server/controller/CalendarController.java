package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SaveCalendarEventDTO;
import com.linkx.server.controller.vo.CalendarEventVO;
import com.linkx.server.service.CalendarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日历控制器
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
            @PathVariable Long eventId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.get(userId, eventId));
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
            @PathVariable Long eventId,
            @Valid @RequestBody SaveCalendarEventDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(calendarService.update(userId, eventId, dto));
    }

    /**
     * 删除事件
     */
    @DeleteMapping("/{eventId}")
    public Result<Void> delete(
            @PathVariable Long eventId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        calendarService.delete(userId, eventId);
        return Result.success(null);
    }
}
