package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.Note;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.NoteMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.ComplianceService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DeviceSessionService deviceSessionService;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

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

        List<Map<String, Object>> messages = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getSenderId).eq(userId)
                        .orderBy(ImMessage::getCreateTime, false)
                        .limit(RECENT_MESSAGE_LIMIT)
        ).stream().map(msg -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("conversationId", msg.getConversationId());
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
    public void purgeUserData(Long userId) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 逻辑删除本人发送的消息正文
        List<ImMessage> sent = messageMapper.selectListByQuery(
                QueryWrapper.create().where(ImMessage::getSenderId).eq(userId)
        );
        for (ImMessage msg : sent) {
            msg.setContent("[已清除]");
            msg.setFileUrl(null);
            msg.setFileName(null);
            msg.setDeleted(1);
            messageMapper.update(msg);
        }

        noteMapper.deleteByQuery(QueryWrapper.create().where(Note::getUserId).eq(userId));
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
