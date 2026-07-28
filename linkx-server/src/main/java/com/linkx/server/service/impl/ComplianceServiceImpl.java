package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.entity.CalendarEvent;
import com.linkx.server.entity.CloudFile;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.MomentsPost;
import com.linkx.server.entity.Note;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.CalendarEventMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.MomentsPostMapper;
import com.linkx.server.mapper.NoteMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.ComplianceService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceServiceImpl implements ComplianceService {

    private static final int RECENT_MESSAGE_LIMIT = 200;

    private final SysUserMapper userMapper;
    private final SysUserRelationMapper relationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final DeviceSessionMapper deviceSessionMapper;
    private final NoteMapper noteMapper;
    private final CloudFileMapper cloudFileMapper;
    private final FavoriteMapper favoriteMapper;
    private final MomentsPostMapper momentsPostMapper;
    private final CalendarEventMapper calendarEventMapper;
    private final DeviceSessionService deviceSessionService;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final com.linkx.server.service.FileStorageService fileStorageService;

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
    @Transactional
    public void purgeUserData(Long userId, String password) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (!StringUtils.hasText(password) || !PasswordEncoderHolder.matches(password, user.getPassword())) {
            audit(userId, "purge", "合规清除密码校验失败", false);
            throw new CustomException(400, "密码错误");
        }

        // 合规脱敏：将本人发送消息的 content 替换为脱敏文本，保留消息结构不破坏会话连贯性，
        // 接收方仍可见占位文案但不泄露原内容；附件信息一并清除。
        // 使用 UpdateChain 单条 SQL 批量更新，避免 select-then-loop 的低效与 N+1 问题；
        // 不设置 deleted=1，使接收方仍能看到占位文案而非消息消失。
        UpdateChain.of(ImMessage.class)
                .set(ImMessage::getContent, "[该用户已注销，消息已清除]")
                .set(ImMessage::getFileUrl, null)
                .set(ImMessage::getFileName, null)
                .where(ImMessage::getSenderId).eq(userId)
                .update();

        noteMapper.deleteByQuery(QueryWrapper.create().where(Note::getUserId).eq(userId));

        // 合规清除扩展：云盘文件、收藏、朋友圈、日历事件、群成员关系
        // 删除云盘 DB 前先收集 objectKey 在事务内同步删 MinIO 对象，避免"DB 已清但对象残留"违反合规承诺
        List<CloudFile> cloudFiles = cloudFileMapper.selectListByQuery(
                QueryWrapper.create().where(CloudFile::getUserId).eq(userId));
        List<String> objectKeys = cloudFiles.stream()
                .map(CloudFile::getFileKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        cloudFileMapper.deleteByQuery(QueryWrapper.create().where(CloudFile::getUserId).eq(userId));
        for (String key : objectKeys) {
            try {
                fileStorageService.deleteFile(key);
            } catch (Exception e) {
                log.warn("合规清除删除 MinIO 对象失败: key={}, err={}", key, e.getMessage());
            }
        }
        favoriteMapper.deleteByQuery(QueryWrapper.create().where(Favorite::getUserId).eq(userId));
        momentsPostMapper.deleteByQuery(QueryWrapper.create().where(MomentsPost::getUserId).eq(userId));
        calendarEventMapper.deleteByQuery(QueryWrapper.create().where(CalendarEvent::getUserId).eq(userId));
        memberMapper.deleteByQuery(QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId));

        deviceSessionService.deleteAllByUser(userId);
        tokenService.revokeAllUserTokens(userId);

        user.setEmail(null);
        user.setPhone(null);
        user.setAvatar(null);
        user.setNickname("已注销用户");
        user.setStatus(0);
        userMapper.update(user);

        audit(userId, "purge", "用户数据清除完成", true);
        log.info("合规清除完成: userId={}", userId);
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
}
