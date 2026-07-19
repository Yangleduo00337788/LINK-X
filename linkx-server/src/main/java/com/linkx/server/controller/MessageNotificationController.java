package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.MessageNotificationVO;
import com.linkx.server.service.MessageNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class MessageNotificationController {

    private final MessageNotificationService notificationService;
    private final JwtUtils jwtUtils;

    /**
     * 获取未读通知列表
     */
    @GetMapping("/unread")
    public Result<List<MessageNotificationVO>> listUnread(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(notificationService.listUnread(userId));
    }

    /**
     * 获取消息通知
     * <p>
     * 默认返回全部通知；{@code mentionOnly=true} 时仅返回 @我的通知 (type=moments_mention)。
     * </p>
     */
    @GetMapping("/mine")
    public Result<List<MessageNotificationVO>> listMine(
            @RequestParam(value = "mentionOnly", defaultValue = "false") boolean mentionOnly,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(notificationService.listMineMentions(userId, mentionOnly));
    }

    /**
     * 获取所有通知列表
     */
    @GetMapping
    public Result<List<MessageNotificationVO>> listAll(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(notificationService.listAll(userId));
    }

    /**
     * 清空当前用户全部通知
     */
    @DeleteMapping("/clear")
    public Result<Integer> clearAll(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        int cleared = notificationService.clearAll(userId);
        return Result.success(cleared);
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> getUnreadCount(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        int count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    /**
     * 标记通知为已读
     */
    @PostMapping("/{notificationId}/read")
    public Result<Void> markAsRead(
            @PathVariable String notificationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        notificationService.markAsRead(userId, parseId(notificationId));
        return Result.success(null);
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{notificationId}")
    public Result<Void> delete(
            @PathVariable String notificationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        notificationService.delete(userId, parseId(notificationId));
        return Result.success(null);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
