package com.linkx.server.service;

import com.linkx.server.controller.dto.SaveCalendarEventDTO;
import com.linkx.server.controller.vo.CalendarEventVO;

import java.util.List;

/**
 * 日历服务接口
 */
public interface CalendarService {

    /**
     * 获取用户所有日历事件
     */
    List<CalendarEventVO> list(Long userId);

    /**
     * 获取指定日期的事件
     */
    List<CalendarEventVO> listByDate(Long userId, String date);

    /**
     * 获取单条事件
     */
    CalendarEventVO get(Long userId, Long eventId);

    /**
     * 创建事件
     */
    CalendarEventVO create(Long userId, SaveCalendarEventDTO dto);

    /**
     * 更新事件
     */
    CalendarEventVO update(Long userId, Long eventId, SaveCalendarEventDTO dto);

    /**
     * 删除事件
     */
    void delete(Long userId, Long eventId);

    /**
     * 触发日程提醒：写入消息通知列表（不弹系统窗）
     */
    void fireReminder(Long userId, Long eventId);
}
