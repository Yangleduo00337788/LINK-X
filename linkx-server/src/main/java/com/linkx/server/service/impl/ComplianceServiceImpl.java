package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.entity.BalanceLog;
import com.linkx.server.entity.CalendarEvent;
import com.linkx.server.entity.CloudActivity;
import com.linkx.server.entity.CloudFile;
import com.linkx.server.entity.CloudFileTag;
import com.linkx.server.entity.CloudFolder;
import com.linkx.server.entity.CloudShare;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.FavoriteStorage;
import com.linkx.server.entity.FavoriteTag;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.GroupInvitation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.MessageNotification;
import com.linkx.server.entity.MomentsComment;
import com.linkx.server.entity.MomentsImage;
import com.linkx.server.entity.MomentsLike;
import com.linkx.server.entity.MomentsPost;
import com.linkx.server.entity.Note;
import com.linkx.server.entity.RedPacket;
import com.linkx.server.entity.RedPacketRecord;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysFriendRequest;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserBlacklist;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.entity.UserBalance;
import com.linkx.server.entity.UserPreference;
import com.linkx.server.entity.UserStorage;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.BalanceLogMapper;
import com.linkx.server.mapper.CalendarEventMapper;
import com.linkx.server.mapper.CloudActivityMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.CloudFileTagMapper;
import com.linkx.server.mapper.CloudFolderMapper;
import com.linkx.server.mapper.CloudShareMapper;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FavoriteStorageMapper;
import com.linkx.server.mapper.FavoriteTagMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.MomentsCommentMapper;
import com.linkx.server.mapper.MomentsImageMapper;
import com.linkx.server.mapper.MomentsLikeMapper;
import com.linkx.server.mapper.MomentsPostMapper;
import com.linkx.server.mapper.NoteMapper;
import com.linkx.server.mapper.RedPacketMapper;
import com.linkx.server.mapper.RedPacketRecordMapper;
import com.linkx.server.mapper.SysFriendRequestMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.mapper.UserBalanceMapper;
import com.linkx.server.mapper.UserBlacklistMapper;
import com.linkx.server.mapper.UserPreferenceMapper;
import com.linkx.server.mapper.UserStorageMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.ComplianceService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceServiceImpl implements ComplianceService {

    private static final int RECENT_MESSAGE_LIMIT = 200;
    private static final String DEFAULT_AVATAR = "/default-avatar.svg";

    private final SysUserMapper userMapper;
    private final SysUserRelationMapper relationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final DeviceSessionMapper deviceSessionMapper;
    private final NoteMapper noteMapper;
    private final CloudFileMapper cloudFileMapper;
    private final CloudFolderMapper cloudFolderMapper;
    private final CloudFileTagMapper cloudFileTagMapper;
    private final CloudShareMapper cloudShareMapper;
    private final CloudActivityMapper cloudActivityMapper;
    private final FavoriteMapper favoriteMapper;
    private final FavoriteTagMapper favoriteTagMapper;
    private final FavoriteStorageMapper favoriteStorageMapper;
    private final MomentsPostMapper momentsPostMapper;
    private final MomentsImageMapper momentsImageMapper;
    private final MomentsLikeMapper momentsLikeMapper;
    private final MomentsCommentMapper momentsCommentMapper;
    private final CalendarEventMapper calendarEventMapper;
    private final UserBalanceMapper userBalanceMapper;
    private final BalanceLogMapper balanceLogMapper;
    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper redPacketRecordMapper;
    private final FeedbackMapper feedbackMapper;
    private final MessageNotificationMapper messageNotificationMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final UserStorageMapper userStorageMapper;
    private final GroupInvitationMapper groupInvitationMapper;
    private final SysFriendRequestMapper friendRequestMapper;
    private final UserBlacklistMapper userBlacklistMapper;
    private final DeviceSessionService deviceSessionService;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;

    @Override
    public UserDataExportVO exportUserData(Long userId) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        List<Map<String, Object>> friends = relationMapper.selectListByQuery(
                QueryWrapper.create().where(SysUserRelation::getUserId).eq(userId)
        ).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("friendId", r.getFriendId());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> conversations = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        ).stream().map(mbr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("conversationId", mbr.getConversationId());
            m.put("role", mbr.getRole());
            m.put("joinTime", mbr.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        // 导出用户参与的所有会话消息（含发送和接收），满足 GDPR 数据可携权
        List<Long> conversationIds = conversations.stream()
                .map(m -> (Long) m.get("conversationId"))
                .collect(Collectors.toList());
        List<Map<String, Object>> messages = (conversationIds.isEmpty()
                ? java.util.Collections.<ImMessage>emptyList()
                : messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getConversationId).in(conversationIds)
                        .orderBy(ImMessage::getCreateTime, false)
                        .limit(RECENT_MESSAGE_LIMIT)
        )).stream().map(msg -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("conversationId", msg.getConversationId());
            m.put("senderId", msg.getSenderId());
            m.put("type", msg.getType());
            m.put("content", msg.getContent());
            m.put("createTime", msg.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> devices = deviceSessionMapper.selectListByQuery(
                QueryWrapper.create().where(DeviceSession::getUserId).eq(userId)
        ).stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", d.getDeviceId());
            m.put("deviceName", d.getDeviceName());
            m.put("deviceType", d.getDeviceType());
            m.put("ip", d.getIp());
            m.put("lastActive", d.getLastActive());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> notes = noteMapper.selectListByQuery(
                QueryWrapper.create().where(Note::getUserId).eq(userId)
        ).stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("createTime", n.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        UserDataExportVO vo = UserDataExportVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .exportTime(new Date())
                .friends(friends)
                .conversations(conversations)
                .recentMessages(messages)
                .devices(devices)
                .notes(notes)
                .build();

        audit(userId, "export", "用户导出个人数据", true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeUserData(Long userId, String password) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (!StringUtils.hasText(password) || !PasswordEncoderHolder.matches(password, user.getPassword())) {
            audit(userId, "purge", "合规清除密码校验失败", false);
            throw new CustomException(400, "密码错误");
        }

        // 先收集 MinIO object key，DB 提交后再删，避免事务回滚后对象已丢
        Set<String> objectKeys = new LinkedHashSet<>();
        collectMinioKey(objectKeys, user.getAvatar());

        UserPreference preference = userPreferenceMapper.selectOneById(userId);
        if (preference != null) {
            collectMinioKey(objectKeys, preference.getMomentsBackground());
        }

        List<CloudFile> cloudFiles = cloudFileMapper.selectListByQuery(
                QueryWrapper.create().where(CloudFile::getUserId).eq(userId));
        for (CloudFile f : cloudFiles) {
            collectMinioKey(objectKeys, f.getFileKey());
        }

        List<MomentsPost> ownPosts = momentsPostMapper.selectListByQuery(
                QueryWrapper.create().where(MomentsPost::getUserId).eq(userId));
        List<Long> ownPostIds = ownPosts.stream().map(MomentsPost::getId).toList();
        if (!ownPostIds.isEmpty()) {
            List<MomentsImage> images = momentsImageMapper.selectListByQuery(
                    QueryWrapper.create().where(MomentsImage::getPostId).in(ownPostIds));
            for (MomentsImage img : images) {
                collectMinioKey(objectKeys, img.getUrl());
            }
        }

        // 本人发送消息：脱敏保留结构，不破坏会话连贯性
        UpdateChain.of(ImMessage.class)
                .set(ImMessage::getContent, "[该用户已注销，消息已清除]")
                .set(ImMessage::getFileUrl, null)
                .set(ImMessage::getFileName, null)
                .where(ImMessage::getSenderId).eq(userId)
                .update();

        // 资金：先领取记录，再红包，再流水与余额
        List<RedPacket> sentPackets = redPacketMapper.selectListByQuery(
                QueryWrapper.create().where(RedPacket::getSenderId).eq(userId));
        List<Long> sentPacketIds = sentPackets.stream().map(RedPacket::getId).toList();
        redPacketRecordMapper.deleteByQuery(
                QueryWrapper.create().where(RedPacketRecord::getUserId).eq(userId));
        if (!sentPacketIds.isEmpty()) {
            redPacketRecordMapper.deleteByQuery(
                    QueryWrapper.create().where(RedPacketRecord::getRedPacketId).in(sentPacketIds));
        }
        redPacketMapper.deleteByQuery(
                QueryWrapper.create().where(RedPacket::getSenderId).eq(userId));
        balanceLogMapper.deleteByQuery(
                QueryWrapper.create().where(BalanceLog::getUserId).eq(userId));
        userBalanceMapper.deleteByQuery(
                QueryWrapper.create().where(UserBalance::getUserId).eq(userId));

        // 社交关系（双向）
        relationMapper.deleteByQuery(QueryWrapper.create()
                .where(SysUserRelation::getUserId).eq(userId)
                .or(SysUserRelation::getFriendId).eq(userId));
        friendRequestMapper.deleteByQuery(QueryWrapper.create()
                .where(SysFriendRequest::getFromUserId).eq(userId)
                .or(SysFriendRequest::getToUserId).eq(userId));
        userBlacklistMapper.deleteByQuery(QueryWrapper.create()
                .where(SysUserBlacklist::getUserId).eq(userId)
                .or(SysUserBlacklist::getBlockedUserId).eq(userId));

        // 朋友圈：先子表再帖子；另清用户在他人帖上的赞/评
        if (!ownPostIds.isEmpty()) {
            momentsImageMapper.deleteByQuery(
                    QueryWrapper.create().where(MomentsImage::getPostId).in(ownPostIds));
            momentsLikeMapper.deleteByQuery(
                    QueryWrapper.create().where(MomentsLike::getPostId).in(ownPostIds));
            momentsCommentMapper.deleteByQuery(
                    QueryWrapper.create().where(MomentsComment::getPostId).in(ownPostIds));
            messageNotificationMapper.deleteByQuery(QueryWrapper.create()
                    .where(MessageNotification::getRelatedId).in(ownPostIds)
                    .and(MessageNotification::getType).like("moments_%"));
            momentsPostMapper.deleteByQuery(
                    QueryWrapper.create().where(MomentsPost::getId).in(ownPostIds));
        }
        momentsLikeMapper.deleteByQuery(
                QueryWrapper.create().where(MomentsLike::getUserId).eq(userId));
        momentsCommentMapper.deleteByQuery(
                QueryWrapper.create().where(MomentsComment::getUserId).eq(userId));

        // 通知（收件人 / 发送人）
        messageNotificationMapper.deleteByQuery(QueryWrapper.create()
                .where(MessageNotification::getUserId).eq(userId)
                .or(MessageNotification::getSenderId).eq(userId));

        // 云盘附属 → 文件 → 配额
        cloudShareMapper.deleteByQuery(
                QueryWrapper.create().where(CloudShare::getUserId).eq(userId));
        cloudActivityMapper.deleteByQuery(
                QueryWrapper.create().where(CloudActivity::getUserId).eq(userId));
        cloudFileTagMapper.deleteByQuery(
                QueryWrapper.create().where(CloudFileTag::getUserId).eq(userId));
        cloudFolderMapper.deleteByQuery(
                QueryWrapper.create().where(CloudFolder::getUserId).eq(userId));
        cloudFileMapper.deleteByQuery(
                QueryWrapper.create().where(CloudFile::getUserId).eq(userId));
        userStorageMapper.deleteByQuery(
                QueryWrapper.create().where(UserStorage::getUserId).eq(userId));

        // 收藏 / 偏好 / 反馈 / 邀请 / 笔记 / 日历 / 会话成员
        favoriteMapper.deleteByQuery(
                QueryWrapper.create().where(Favorite::getUserId).eq(userId));
        favoriteTagMapper.deleteByQuery(
                QueryWrapper.create().where(FavoriteTag::getUserId).eq(userId));
        favoriteStorageMapper.deleteByQuery(
                QueryWrapper.create().where(FavoriteStorage::getUserId).eq(userId));
        userPreferenceMapper.deleteByQuery(
                QueryWrapper.create().where(UserPreference::getUserId).eq(userId));
        feedbackMapper.deleteByQuery(
                QueryWrapper.create().where(Feedback::getUserId).eq(userId));
        groupInvitationMapper.deleteByQuery(QueryWrapper.create()
                .where(GroupInvitation::getInviterUserId).eq(userId)
                .or(GroupInvitation::getInviteeUserId).eq(userId));
        noteMapper.deleteByQuery(QueryWrapper.create().where(Note::getUserId).eq(userId));
        calendarEventMapper.deleteByQuery(
                QueryWrapper.create().where(CalendarEvent::getUserId).eq(userId));
        memberMapper.deleteByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId));

        deviceSessionService.deleteAllByUser(userId);
        tokenService.revokeAllUserTokens(userId);

        user.setEmail(null);
        user.setPhone(null);
        user.setAvatar(null);
        user.setNickname("已注销用户");
        user.setSignature(null);
        user.setStatus(0);
        userMapper.update(user);

        List<String> keysToDelete = new ArrayList<>(objectKeys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteMinioKeys(keysToDelete);
                    audit(userId, "purge", "用户数据清除完成", true);
                }
            });
        } else {
            deleteMinioKeys(keysToDelete);
            audit(userId, "purge", "用户数据清除完成", true);
        }
        log.info("合规清除完成: userId={}, pendingMinioKeys={}", userId, keysToDelete.size());
    }

    @Override
    public void audit(Long userId, String action, String detail, boolean success) {
        SysAuditLog.OperationType type = switch (action) {
            case "purge" -> SysAuditLog.OperationType.DATA_PURGE;
            case "retention" -> SysAuditLog.OperationType.DATA_RETENTION;
            default -> SysAuditLog.OperationType.DATA_EXPORT;
        };
        auditLogService.log(type, detail, userId, null, null, null, success, success ? null : detail);
    }

    private void collectMinioKey(Set<String> keys, String keyOrUrl) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return;
        }
        String value = keyOrUrl.trim();
        if (DEFAULT_AVATAR.equals(value) || value.endsWith(DEFAULT_AVATAR)) {
            return;
        }
        if (value.startsWith("/") || value.startsWith("data:") || value.startsWith("blob:")) {
            return;
        }
        if (mediaUrlService.isExternalHttpUrl(value)) {
            return;
        }
        keys.add(value);
    }

    private void deleteMinioKeys(List<String> keys) {
        for (String key : keys) {
            try {
                fileStorageService.deleteFile(key);
            } catch (Exception e) {
                log.warn("合规清除删除 MinIO 对象失败: key={}, err={}", key, e.getMessage());
            }
        }
    }
}
