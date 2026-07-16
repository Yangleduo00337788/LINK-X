package com.linkx.server.service;

import com.linkx.server.controller.vo.MessageNotificationVO;

import java.util.List;

/**
 * 消息通知服务接口
 */
public interface MessageNotificationService {

    /**
     * 获取用户未读通知列表
     */
    List<MessageNotificationVO> listUnread(Long userId);

    /**
     * 获取用户所有通知列表
     */
    List<MessageNotificationVO> listAll(Long userId);

    /**
     * 获取未读通知数量
     */
    int getUnreadCount(Long userId);

    /**
     * 标记通知为已读
     */
    void markAsRead(Long userId, Long notificationId);

    /**
     * 标记所有通知为已读
     */
    void markAllAsRead(Long userId);

    /**
     * 删除通知
     */
    void delete(Long userId, Long notificationId);

    /**
     * 创建通知
     */
    void create(Long userId, Long senderId, String senderName, String senderAvatar, String type, Long relatedId, String content);
}
