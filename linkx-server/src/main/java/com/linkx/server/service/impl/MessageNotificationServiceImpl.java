package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.MessageNotificationVO;
import com.linkx.server.entity.MessageNotification;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageNotificationServiceImpl implements MessageNotificationService {

    private final MessageNotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final MediaUrlService mediaUrlService;

    @Override
    public List<MessageNotificationVO> listUnread(Long userId) {
        List<MessageNotification> notifications = notificationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("read_status", 0)
                        .orderBy("create_time", false)
        );
        return notifications.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<MessageNotificationVO> listAll(Long userId) {
        List<MessageNotification> notifications = notificationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .orderBy("create_time", false)
        );
        return notifications.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<MessageNotificationVO> listMineMentions(Long userId, boolean mentionOnly) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("create_time", false);
        if (mentionOnly) {
            // 「只收到@我的消息」过滤
            wrapper.eq("type", "moments_mention");
        }
        List<MessageNotification> notifications = notificationMapper.selectListByQuery(wrapper);
        return notifications.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public int getUnreadCount(Long userId) {
        Long count = notificationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("read_status", 0)
        );
        return count != null ? count.intValue() : 0;
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        MessageNotification notification = notificationMapper.selectOneById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new CustomException(404, "通知不存在");
        }
        notification.setReadStatus(1);
        notificationMapper.update(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        List<MessageNotification> unreadList = notificationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("read_status", 0)
        );
        for (MessageNotification notification : unreadList) {
            notification.setReadStatus(1);
            notificationMapper.update(notification);
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long notificationId) {
        MessageNotification notification = notificationMapper.selectOneById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new CustomException(404, "通知不存在");
        }
        notificationMapper.deleteById(notificationId);
    }

    @Override
    @Transactional
    public int clearAll(Long userId) {
        if (userId == null) return 0;
        List<MessageNotification> all = notificationMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId)
        );
        if (all.isEmpty()) {
            return 0;
        }
        for (MessageNotification n : all) {
            notificationMapper.deleteById(n.getId());
        }
        log.info("清空用户 {} 的全部消息通知，共 {} 条", userId, all.size());
        return all.size();
    }

    @Override
    @Transactional
    public void create(Long userId, Long senderId, String senderName, String senderAvatar, String type, Long relatedId, String content) {
        // 获取发送者信息
        String name = senderName;
        String avatar = senderAvatar;
        if (senderId != null) {
            SysUser sender = sysUserMapper.selectOneById(senderId);
            if (sender != null) {
                name = sender.getNickname() != null ? sender.getNickname() : sender.getUsername();
                avatar = sender.getAvatar();
            }
        }

        MessageNotification notification = MessageNotification.builder()
                .userId(userId)
                .senderId(senderId)
                .senderName(name)
                .senderAvatar(avatar)
                .type(type)
                .relatedId(relatedId)
                .content(content)
                .readStatus(0)
                .build();
        notificationMapper.insert(notification);
    }

    private MessageNotificationVO toVO(MessageNotification notification) {
        // 库中存 object key；对外签发可访问 URL（与好友/友链头像一致）
        String avatar = notification.getSenderAvatar();
        if (avatar == null || avatar.isBlank()) {
            if (notification.getSenderId() != null) {
                SysUser sender = sysUserMapper.selectOneById(notification.getSenderId());
                if (sender != null) {
                    avatar = sender.getAvatar();
                }
            }
        }
        return MessageNotificationVO.builder()
                .id(notification.getId())
                .senderId(notification.getSenderId())
                .senderName(notification.getSenderName())
                .senderAvatar(mediaUrlService.resolve(avatar))
                .type(notification.getType())
                .category(resolveCategory(notification.getType()))
                .relatedId(notification.getRelatedId())
                .content(notification.getContent())
                .readStatus(notification.getReadStatus())
                .createTime(notification.getCreateTime())
                .build();
    }

    /** 业务通知与系统通知分轨：moments / system / social / other */
    static String resolveCategory(String type) {
        if (type == null || type.isBlank()) {
            return "other";
        }
        if (type.startsWith("moments_")) {
            return "moments";
        }
        if ("calendar_remind".equals(type) || type.startsWith("system_")) {
            return "system";
        }
        if (type.startsWith("friend_") || type.startsWith("group_")) {
            return "social";
        }
        return "other";
    }
}
