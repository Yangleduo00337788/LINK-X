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
     * 获取当前用户在「@我的」场景下的通知列表(支持 type 过滤)。
     *
     * @param userId      接收者
     * @param mentionOnly true 时只保留 type=moments_mention;
     *                    false 时与 listAll 等价
     */
    List<MessageNotificationVO> listMineMentions(Long userId, boolean mentionOnly);

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
     * 清空指定用户全部通知(逻辑删除)
     *
     * @return 被清除条数
     */
    int clearAll(Long userId);

    /**
     * 创建通知
     */
    void create(Long userId, Long senderId, String senderName, String senderAvatar, String type, Long relatedId, String content);
}
