package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.metrics.LinkxMetrics;
import com.linkx.server.common.ImageUploadValidator;
import com.linkx.server.common.InputSanitizer;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ChatSearchHitVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupMemberAvatarVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.RedPacket;
import com.linkx.server.entity.RedPacketRecord;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.RedPacketMapper;
import com.linkx.server.mapper.RedPacketRecordMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.UserPreferenceService;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.admin.AdminRiskEventService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.admin.SysReviewTask;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int RELATION_STATUS_NORMAL = 1;
    private static final int RELATION_STATUS_BLOCKED = 2;
    private static final long RECALL_WINDOW_MS = 2 * 60 * 1000L;
    /** 分片会话发起人绑定（与 FileStorageService 分片 TTL 对齐） */
    private static final String MP_OWNER_PREFIX = "linkx:mp:owner:";
    private static final Duration MP_OWNER_TTL = Duration.ofHours(24);

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;
    private final LinkxProperties linkxProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper redPacketRecordMapper;
    private final UserPreferenceService userPreferenceService;
    private final PresenceService presenceService;
    private final SensitiveWordService sensitiveWordService;
    private final MessageStormService messageStormService;
    private final AuditLogService auditLogService;
    private final AdminRiskEventService adminRiskEventService;
    private final ObjectProvider<AdminReviewService> adminReviewService;
    private final LinkxMetrics linkxMetrics;

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, ImConversationMember> membershipMap = memberships.stream()
                .collect(Collectors.toMap(ImConversationMember::getConversationId, m -> m, (a, b) -> a));

        Set<Long> conversationIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());

        List<ImConversation> conversations = conversationMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversation::getId).in(conversationIds)
        );
        conversations.sort((a, b) -> {
            boolean importantA = isImportant(membershipMap, a.getId());
            boolean importantB = isImportant(membershipMap, b.getId());
            if (importantA != importantB) return importantA ? -1 : 1;
            boolean pinnedA = isPinned(membershipMap, a.getId());
            boolean pinnedB = isPinned(membershipMap, b.getId());
            if (pinnedA != pinnedB) return pinnedA ? -1 : 1;
            Date timeA = a.getLastMessageTime();
            Date timeB = b.getLastMessageTime();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

        Map<Long, SysUser> peerUserMap = loadPeerUsers(userId, conversations);
        Set<Long> peerUserIds = peerUserMap.values().stream()
                .map(SysUser::getId)
                .collect(Collectors.toSet());
        Map<Long, SysUserRelation> relationMap = loadRelationMap(userId, peerUserIds);
        Map<Long, String> remarkMap = new HashMap<>();
        for (Map.Entry<Long, SysUserRelation> e : relationMap.entrySet()) {
            remarkMap.put(e.getKey(), e.getValue().getRemark());
        }
        Map<Long, Boolean> showOnlineMap = userPreferenceService.batchShowsOnlineStatus(peerUserIds);
        Set<Long> groupIds = conversations.stream()
                .filter(c -> c.getType() == ImConversation.TYPE_GROUP)
                .map(ImConversation::getId)
                .collect(Collectors.toSet());
        Map<Long, List<GroupMemberAvatarVO>> groupMemberAvatars = loadGroupMemberAvatarPreviews(groupIds);
        Map<Long, String> groupRemarkMap = loadGroupRemarkMap(userId, groupIds);

        List<ConversationVO> result = new ArrayList<>();
        for (ImConversation conversation : conversations) {
            ImConversationMember membership = membershipMap.get(conversation.getId());
            boolean pinned = membership != null && membership.getPinned() != null && membership.getPinned() == 1;
            boolean important = membership != null && membership.getImportant() != null && membership.getImportant() == 1;
            boolean muted = membership != null && membership.getMuted() != null && membership.getMuted() == 1;

            if (conversation.getType() == ImConversation.TYPE_PRIVATE) {
                SysUser peer = peerUserMap.get(conversation.getId());
                if (peer == null) {
                    continue;
                }
                SysUserRelation relation = relationMap.get(peer.getId());
                // 无关系记录时仍展示（陌生人会话）；仅隐藏非正常且非拉黑的异常状态
                boolean blocked = relation != null
                        && Objects.equals(relation.getStatus(), RELATION_STATUS_BLOCKED);
                if (relation != null
                        && !blocked
                        && !Objects.equals(relation.getStatus(), RELATION_STATUS_NORMAL)) {
                    continue;
                }
                boolean showOnline = !Boolean.FALSE.equals(showOnlineMap.get(peer.getId()));
                boolean peerOnline = showOnline && presenceService.isOnline(peer.getId());
                result.add(toConversationVO(conversation, peer, remarkMap.get(peer.getId()), peerOnline,
                        getUnreadCount(userId, conversation.getId()), pinned, important, muted, blocked));
            } else if (conversation.getType() == ImConversation.TYPE_GROUP) {
                result.add(toGroupConversationVO(
                        conversation,
                        groupMemberAvatars.getOrDefault(conversation.getId(), List.of()),
                        groupRemarkMap.get(conversation.getId()),
                        getUnreadCount(userId, conversation.getId()), pinned, important, muted
                ));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public ConversationVO getOrCreatePrivateConversation(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new CustomException(400, "不能与自己发起聊天");
        }
        assertCanPrivateChat(userId, friendId);

        SysUser friend = sysUserMapper.selectOneById(friendId);
        if (friend == null || friend.getStatus() != 1) {
            throw new CustomException(404, "用户不存在");
        }

        String privateKey = buildPrivateKey(userId, friendId);
        ImConversation conversation = conversationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getType).eq(ImConversation.TYPE_PRIVATE)
                        .and(ImConversation::getPrivateKey).eq(privateKey)
        );

        if (conversation == null) {
            conversation = ImConversation.builder()
                    .type(ImConversation.TYPE_PRIVATE)
                    .privateKey(privateKey)
                    .muteAll(0)
                    .deleted(0)
                    .build();
            try {
                conversationMapper.insert(conversation);

                memberMapper.insert(ImConversationMember.builder()
                        .conversationId(conversation.getId())
                        .userId(userId)
                        .role(ImConversationMember.ROLE_MEMBER)
                        .muted(0)
                        .deleted(0)
                        .build());
                memberMapper.insert(ImConversationMember.builder()
                        .conversationId(conversation.getId())
                        .userId(friendId)
                        .role(ImConversationMember.ROLE_MEMBER)
                        .muted(0)
                        .deleted(0)
                        .build());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发创建：另一线程已插入，重新查询即可
                conversation = conversationMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImConversation::getType).eq(ImConversation.TYPE_PRIVATE)
                                .and(ImConversation::getPrivateKey).eq(privateKey)
                );
                if (conversation == null) {
                    throw new CustomException(500, "会话创建失败，请重试");
                }
            }
        } else {
            ensurePrivateMembership(conversation.getId(), userId);
            ensurePrivateMembership(conversation.getId(), friendId);
        }

        Map<Long, String> remarkMap = loadRemarkMap(userId, Set.of(friendId));
        return toConversationVO(conversation, friend, remarkMap.get(friendId), resolvePeerOnline(friendId),
                getUnreadCount(userId, conversation.getId()), false, false, false, false);
    }

    @Override
    public List<MessageVO> listMessages(Long userId, Long conversationId, Long beforeMessageId, int limit) {
        assertConversationMember(userId, conversationId);
        int pageSize = limit <= 0 ? DEFAULT_MESSAGE_LIMIT : Math.min(limit, MAX_MESSAGE_LIMIT);

        // 雪花 ID 单调递增：用 id 游标避免同秒 create_time 导致漏页/重页
        QueryWrapper query = QueryWrapper.create()
                .where(ImMessage::getConversationId).eq(conversationId)
                .orderBy(ImMessage::getId, false)
                .limit(pageSize);

        if (beforeMessageId != null) {
            query.and(ImMessage::getId).lt(beforeMessageId);
        }

        List<ImMessage> messages = messageMapper.selectListByQuery(query);
        if (messages.isEmpty()) {
            return List.of();
        }

        messages.sort(Comparator.comparing(ImMessage::getId));
        Set<Long> senderIds = messages.stream().map(ImMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(senderIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        Long lastReadMessageId = loadLastReadMessageId(userId, conversationId);
        Map<Long, RedPacket> redPacketMap = loadRedPacketsForMessages(messages);
        Map<Long, RedPacketRecord> myRedPacketRecordMap = loadMyRedPacketRecords(userId, redPacketMap.keySet());
        List<MessageVO> result = new ArrayList<>();
        for (ImMessage message : messages) {
            SysUser sender = senderMap.get(message.getSenderId());
            result.add(toMessageVO(message, sender, userId, lastReadMessageId, redPacketMap, myRedPacketRecordMap));
        }
        return result;
    }

    @Override
    @Transactional
    public MessageVO sendMessage(Long userId, SendMessageDTO dto) {
        assertConversationMember(userId, dto.getConversationId());

        if (StringUtils.hasText(dto.getClientMsgId())) {
            String dedupKey = buildClientMsgDedupKey(userId, dto.getClientMsgId());
            Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(
                    dedupKey, "1", Duration.ofMinutes(10));
            if (firstTime == null || !firstTime) {
                ImMessage existing = messageMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImMessage::getSenderId).eq(userId)
                                .and(ImMessage::getClientMsgId).eq(dto.getClientMsgId())
                                .orderBy(ImMessage::getCreateTime, false)
                                .limit(1));
                if (existing != null) {
                    SysUser sender = sysUserMapper.selectOneById(userId);
                    return toMessageVO(existing, sender, userId, loadLastReadMessageId(userId, dto.getConversationId()));
                }
                // 去重键已占用但消息尚未可见：并发写入中或刚回滚，禁止继续 insert 绕过去重
                throw new CustomException(409, "消息处理中，请稍后重试");
            } else {
                // 事务回滚时补偿删除去重键，避免 10 分钟内阻塞同 client_msg_id 的合法重试
                registerDedupKeyRollbackCleanup(dedupKey);
            }
        }

        ImConversation conversation = conversationMapper.selectOneById(dto.getConversationId());
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        if (conversation.getType() == ImConversation.TYPE_PRIVATE) {
            Long peerId = resolvePrivatePeerId(userId, conversation.getId());
            if (isBlockedEitherWay(userId, peerId)) {
                throw new CustomException(403, "已屏蔽该联系人，无法发送消息");
            }
            if (!isFriend(userId, peerId)) {
                throw new CustomException(403, "对方已不是好友，无法发送消息");
            }
        } else if (conversation.getType() == ImConversation.TYPE_GROUP) {
            assertGroupSpeakAllowed(userId, conversation);
            // 超大群消息风暴控制
            checkGroupMessageStormLimit(userId, conversation.getId());
        }

        String msgType = resolveMsgType(userId, dto);
        validateMessagePayload(msgType, dto);

        String storedFileUrl = dto.getFileUrl();
        if (!ImMessage.TYPE_TEXT.equals(msgType)
                && !ImMessage.TYPE_LOCATION.equals(msgType)
                && !ImMessage.TYPE_RED_PACKET.equals(msgType)
                && !ImMessage.TYPE_CONFERENCE.equals(msgType)) {
            storedFileUrl = normalizeAndAuthorizeMediaKey(userId, dto.getFileUrl());
        }

        // 敏感词过滤：文本消息进行 DFA 过滤
        String content = resolveContent(msgType, dto, storedFileUrl);
        boolean sensitiveAlert = false;
        String sensitiveFailReason = null;
        String sensitiveMatchedWords = null;
        String originalSensitiveContent = content;
        if (ImMessage.TYPE_TEXT.equals(msgType) && content != null) {
            SensitiveWordService.FilterResult filterResult = sensitiveWordService.filter(content);
            if (!filterResult.matchedWords().isEmpty()) {
                String failReason = filterResult.blocked()
                        ? "blocked"
                        : (filterResult.filtered() ? "filtered" : (filterResult.alerted() ? "alert" : "matched"));
                sensitiveFailReason = failReason;
                sensitiveMatchedWords = String.join(",", filterResult.matchedWords());
                auditLogService.log(
                        SysAuditLog.OperationType.SENSITIVE_WORD_MATCH,
                        "敏感词命中: " + sensitiveMatchedWords,
                        userId,
                        null,
                        null,
                        null,
                        !filterResult.blocked(),
                        failReason
                );
                adminRiskEventService.recordSensitiveMatch(
                        userId,
                        sensitiveMatchedWords,
                        failReason,
                        dto.getConversationId());
            }
            if (filterResult.blocked()) {
                enqueueSensitiveReview(
                        userId,
                        SysReviewTask.TARGET_CONVERSATION,
                        String.valueOf(dto.getConversationId()),
                        dto.getConversationId(),
                        originalSensitiveContent,
                        sensitiveMatchedWords,
                        "blocked");
                throw new CustomException(400, "消息包含违禁内容，无法发送");
            }
            content = filterResult.text();
            sensitiveAlert = filterResult.alerted();
        }

        ImMessage message = ImMessage.builder()
                .conversationId(dto.getConversationId())
                .senderId(userId)
                .type(msgType)
                .content(content)
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .fileUrl(storedFileUrl)
                .clientMsgId(dto.getClientMsgId())
                .deliveryStatus(StringUtils.hasText(dto.getDeliveryStatus()) ? dto.getDeliveryStatus().trim() : "delivered")
                .readStatus(0)
                .voiceDuration(dto.getVoiceDuration())
                .deleted(0)
                .build();
        messageMapper.insert(message);

        if (sensitiveFailReason != null && !"blocked".equals(sensitiveFailReason)) {
            enqueueSensitiveReview(
                    userId,
                    SysReviewTask.TARGET_MESSAGE,
                    String.valueOf(message.getId()),
                    dto.getConversationId(),
                    originalSensitiveContent,
                    sensitiveMatchedWords,
                    sensitiveFailReason);
        }

        Date now = message.getCreateTime() != null ? message.getCreateTime() : new Date();
        conversation.setLastMessageContent(buildPreview(message));
        conversation.setLastMessageTime(now);
        conversationMapper.update(conversation);

        linkxMetrics.recordMessageSent();
        SysUser sender = sysUserMapper.selectOneById(userId);
        MessageVO vo = toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversation.getId()));
        if (sensitiveAlert) {
            vo.setSensitiveAlert(Boolean.TRUE);
        }
        return vo;
    }

    @Override
    @Transactional
    public MessageVO recallMessage(Long userId, Long conversationId, Long messageId) {
        assertConversationMember(userId, conversationId);

        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw new CustomException(404, "消息不存在");
        }
        if (!userId.equals(message.getSenderId())) {
            throw new CustomException(403, "只能撤回自己发送的消息");
        }
        if (ImMessage.TYPE_RECALL.equals(message.getType())) {
            SysUser sender = sysUserMapper.selectOneById(userId);
            return toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversationId));
        }

        Date createTime = message.getCreateTime();
        if (createTime == null || System.currentTimeMillis() - createTime.getTime() > RECALL_WINDOW_MS) {
            throw new CustomException(400, "超过撤回时限");
        }

        return markMessageRecalled(message, userId);
    }

    @Override
    @Transactional
    public MessageVO adminForceRecallMessage(Long messageId) {
        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null) {
            throw new CustomException(404, "消息不存在");
        }
        if (ImMessage.TYPE_RECALL.equals(message.getType())) {
            SysUser sender = sysUserMapper.selectOneById(message.getSenderId());
            return toMessageVO(message, sender, message.getSenderId(), null);
        }
        return markMessageRecalled(message, message.getSenderId());
    }

    private MessageVO markMessageRecalled(ImMessage message, Long viewerUserId) {
        message.setType(ImMessage.TYPE_RECALL);
        message.setContent("");
        message.setFileName("");
        message.setFileSize(0L);
        message.setFileUrl("");
        message.setVoiceDuration(0);
        messageMapper.update(message);

        refreshConversationLastMessage(message.getConversationId());

        SysUser sender = sysUserMapper.selectOneById(message.getSenderId());
        Long uid = viewerUserId != null ? viewerUserId : message.getSenderId();
        return toMessageVO(message, sender, uid, loadLastReadMessageId(uid, message.getConversationId()));
    }

    private void enqueueSensitiveReview(Long userId,
                                        String targetType,
                                        String targetId,
                                        Long conversationId,
                                        String content,
                                        String matchedWords,
                                        String failReason) {
        AdminReviewService reviewService = adminReviewService.getIfAvailable();
        if (reviewService == null) {
            return;
        }
        try {
            reviewService.createFromSensitiveHit(
                    userId, targetType, targetId, conversationId, content, matchedWords, failReason);
        } catch (Exception e) {
            // 入审失败不影响主链路
        }
    }

    @Override
    @Transactional
    public MessageVO postSystemMessage(Long operatorId, Long conversationId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new CustomException(400, "系统提示不能为空");
        }
        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }

        String text = InputSanitizer.sanitizeText(content.trim(), 500);
        Date now = new Date();
        ImMessage message = ImMessage.builder()
                .conversationId(conversationId)
                .senderId(operatorId != null ? operatorId : 0L)
                .type(ImMessage.TYPE_SYSTEM)
                .content(text)
                .deliveryStatus("delivered")
                .readStatus(0)
                .createTime(now)
                .deleted(0)
                .build();
        messageMapper.insert(message);
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }

        conversation.setLastMessageContent(text);
        conversation.setLastMessageTime(message.getCreateTime());
        conversationMapper.update(conversation);

        SysUser sender = operatorId != null ? sysUserMapper.selectOneById(operatorId) : null;
        return toMessageVO(message, sender, operatorId, loadLastReadMessageId(operatorId, conversationId));
    }

    @Override
    @Transactional
    public MessageVO postConferenceInviteMessage(
            Long senderId,
            Long conversationId,
            Long conferenceId,
            String title,
            String callType,
            String scene,
            boolean hasPassword) {
        if (senderId == null || conversationId == null || conferenceId == null) {
            throw new CustomException(400, "会议邀请参数不完整");
        }
        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        SysUser sender = sysUserMapper.selectOneById(senderId);
        String name = sender != null
                ? (StringUtils.hasText(sender.getNickname())
                    ? sender.getNickname()
                    : (StringUtils.hasText(sender.getUsername()) ? sender.getUsername() : "用户"))
                : "用户";
        boolean isCall = "call".equalsIgnoreCase(scene);
        boolean video = !"voice".equalsIgnoreCase(callType);
        String meetingTitle;
        String typeLabel;
        if (isCall) {
            typeLabel = video ? "视频通话" : "语音通话";
            meetingTitle = StringUtils.hasText(title) ? title.trim() : typeLabel;
        } else {
            typeLabel = "会议";
            meetingTitle = StringUtils.hasText(title) ? title.trim() : "多人会议";
        }
        String text = name + "发起了" + typeLabel + "「" + meetingTitle + "」";

        Date now = new Date();
        ImMessage message = ImMessage.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(ImMessage.TYPE_CONFERENCE)
                .content(text)
                .fileName(meetingTitle)
                .fileUrl(String.valueOf(conferenceId))
                .fileSize(hasPassword ? 1L : 0L)
                .deliveryStatus("delivered")
                .readStatus(0)
                .createTime(now)
                .deleted(0)
                .build();
        messageMapper.insert(message);
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }

        conversation.setLastMessageContent(buildPreview(message));
        conversation.setLastMessageTime(message.getCreateTime());
        conversationMapper.update(conversation);

        return toMessageVO(message, sender, senderId, loadLastReadMessageId(senderId, conversationId));
    }

    @Override
    @Transactional
    public MessageVO postCallInviteMessage(
            Long senderId,
            Long conversationId,
            String callId,
            String callType) {
        if (senderId == null || conversationId == null || !StringUtils.hasText(callId)) {
            throw new CustomException(400, "通话邀请参数不完整");
        }
        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        SysUser sender = sysUserMapper.selectOneById(senderId);
        String name = sender != null
                ? (StringUtils.hasText(sender.getNickname())
                    ? sender.getNickname()
                    : (StringUtils.hasText(sender.getUsername()) ? sender.getUsername() : "用户"))
                : "用户";
        boolean video = !"voice".equalsIgnoreCase(callType);
        String typeLabel = video ? "视频通话" : "语音通话";
        String text = name + "发起了" + typeLabel;

        Date now = new Date();
        ImMessage message = ImMessage.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(ImMessage.TYPE_CONFERENCE)
                .content(text)
                .fileName(typeLabel)
                .fileUrl(callId.trim())
                .fileSize(0L)
                .deliveryStatus("delivered")
                .readStatus(0)
                .createTime(now)
                .deleted(0)
                .build();
        messageMapper.insert(message);
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }

        conversation.setLastMessageContent(buildPreview(message));
        conversation.setLastMessageTime(message.getCreateTime());
        conversationMapper.update(conversation);

        return toMessageVO(message, sender, senderId, loadLastReadMessageId(senderId, conversationId));
    }

    @Override
    @Transactional
    public MessageVO updateCallTipMessage(Long conversationId, String callId, String content) {
        if (conversationId == null || !StringUtils.hasText(callId) || !StringUtils.hasText(content)) {
            return null;
        }
        ImMessage message = messageMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getConversationId).eq(conversationId)
                        .and(ImMessage::getType).eq(ImMessage.TYPE_CONFERENCE)
                        .and(ImMessage::getFileUrl).eq(callId.trim())
                        .and(ImMessage::getDeleted).eq(0)
                        .orderBy(ImMessage::getId, false)
                        .limit(1)
        );
        if (message == null) {
            return null;
        }
        message.setContent(content.trim());
        messageMapper.update(message);

        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation != null) {
            conversation.setLastMessageContent(buildPreview(message));
            conversation.setLastMessageTime(message.getCreateTime() != null ? message.getCreateTime() : new Date());
            conversationMapper.update(conversation);
        }

        SysUser sender = message.getSenderId() != null
                ? sysUserMapper.selectOneById(message.getSenderId())
                : null;
        return toMessageVO(message, sender, message.getSenderId(), null);
    }

    @Override
    public ChatFileUploadVO uploadChatFile(Long userId, Long conversationId, MultipartFile file) {
        assertConversationMember(userId, conversationId);
        try {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                ImageUploadValidator.assertSupportedImage(file);
            }
            String objectKey = fileStorageService.uploadFile(file, null);
            objectKeyOwnershipService.claim(userId, objectKey);
            String signedUrl = mediaUrlService.resolveFile(objectKey);
            return ChatFileUploadVO.builder()
                    .url(signedUrl)
                    .fileKey(objectKey)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(400, e.getMessage());
        }
    }

    @Override
    public FileStorageService.StoredObject openMessageFile(Long userId, Long messageId) {
        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null) {
            throw new CustomException(404, "消息不存在");
        }
        assertConversationMember(userId, message.getConversationId());
        String key = message.getFileUrl();
        if (key == null || key.isBlank()) {
            throw new CustomException(400, "该消息没有附件");
        }
        if (ImMessage.TYPE_RED_PACKET.equals(message.getType())) {
            throw new CustomException(400, "红包消息不支持文件下载");
        }
        if (ImMessage.TYPE_CONFERENCE.equals(message.getType())) {
            throw new CustomException(400, "会议邀请不支持文件下载");
        }
        return fileStorageService.openObject(key);
    }

    @Override
    public String getMessageFileName(Long userId, Long messageId) {
        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null) {
            throw new CustomException(404, "消息不存在");
        }
        assertConversationMember(userId, message.getConversationId());
        if (message.getFileName() != null && !message.getFileName().isBlank()) {
            return message.getFileName();
        }
        return "file";
    }

    @Override
    public String refreshMessageMediaUrl(Long userId, Long messageId) {
        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null) {
            throw new CustomException(404, "消息不存在");
        }
        assertConversationMember(userId, message.getConversationId());
        String key = message.getFileUrl();
        if (key == null || key.isBlank()) {
            // 图片消息偶发只写在 content
            key = message.getContent();
        }
        if (key == null || key.isBlank()) {
            throw new CustomException(400, "该消息没有可刷新的媒体");
        }
        if (ImMessage.TYPE_RED_PACKET.equals(message.getType())) {
            throw new CustomException(400, "红包消息不支持媒体刷新");
        }
        if (ImMessage.TYPE_CONFERENCE.equals(message.getType())) {
            throw new CustomException(400, "会议邀请不支持媒体刷新");
        }
        String signed = mediaUrlService.resolveFile(key);
        if (signed == null || signed.isBlank()) {
            throw new CustomException(400, "无法刷新媒体地址");
        }
        return signed;
    }

    @Override
    public List<ChatSearchHitVO> searchMessages(Long userId, String keyword, String type, Long conversationId,
                                                Long fromTime, Long toTime, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String q = keyword.trim();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        int cap = Math.min(Math.max(limit, 1), 100);

        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<Long> allowedIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());
        if (conversationId != null) {
            if (!allowedIds.contains(conversationId)) {
                throw new CustomException(403, "无权访问该会话");
            }
            allowedIds = Set.of(conversationId);
        }

        QueryWrapper qw = QueryWrapper.create()
                .where(ImMessage::getConversationId).in(allowedIds)
                .and(ImMessage::getType).ne(ImMessage.TYPE_RECALL)
                .and(ImMessage::getContent).like(q)
                .orderBy(ImMessage::getCreateTime, false)
                .limit(cap);
        if (StringUtils.hasText(type)) {
            qw.and(ImMessage::getType).eq(type.trim());
        }
        applySearchTimeRange(qw, fromTime, toTime);

        List<ImMessage> messages = messageMapper.selectListByQuery(qw);
        if (messages.isEmpty()) {
            QueryWrapper fileQw = QueryWrapper.create()
                    .where(ImMessage::getConversationId).in(allowedIds)
                    .and(ImMessage::getType).ne(ImMessage.TYPE_RECALL)
                    .and(ImMessage::getFileName).like(q)
                    .orderBy(ImMessage::getCreateTime, false)
                    .limit(cap);
            if (StringUtils.hasText(type)) {
                fileQw.and(ImMessage::getType).eq(type.trim());
            }
            applySearchTimeRange(fileQw, fromTime, toTime);
            messages = messageMapper.selectListByQuery(fileQw);
        }

        Set<Long> convIds = messages.stream().map(ImMessage::getConversationId).collect(Collectors.toSet());
        Set<Long> senderIds = messages.stream().map(ImMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, ImConversation> convMap = convIds.isEmpty() ? Map.of() :
                conversationMapper.selectListByQuery(QueryWrapper.create().where(ImConversation::getId).in(convIds))
                        .stream().collect(Collectors.toMap(ImConversation::getId, Function.identity(), (a, b) -> a));
        Map<Long, SysUser> userMap = senderIds.isEmpty() ? Map.of() :
                sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(senderIds))
                        .stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<ChatSearchHitVO> hits = new ArrayList<>();
        for (ImMessage msg : messages) {
            ImConversation conv = convMap.get(msg.getConversationId());
            SysUser sender = userMap.get(msg.getSenderId());
            String raw = msg.getContent() != null && !msg.getContent().isBlank()
                    ? msg.getContent()
                    : msg.getFileName();
            hits.add(ChatSearchHitVO.builder()
                    .messageId(msg.getId())
                    .conversationId(msg.getConversationId())
                    .conversationName(conv != null ? conv.getName() : null)
                    .conversationType(conv != null ? conv.getType() : null)
                    .senderId(msg.getSenderId())
                    .senderNickname(sender != null ? sender.getNickname() : null)
                    .type(msg.getType())
                    .content(msg.getContent())
                    .fileName(msg.getFileName())
                    .fileUrl(ImMessage.TYPE_RED_PACKET.equals(msg.getType())
                            || ImMessage.TYPE_CONFERENCE.equals(msg.getType())
                            ? msg.getFileUrl()
                            : mediaUrlService.resolveFile(msg.getFileUrl()))
                    .createTime(msg.getCreateTime() == null ? null : msg.getCreateTime().getTime())
                    .highlight(buildSearchHighlight(raw, q))
                    .build());
        }
        return hits;
    }

    private void applySearchTimeRange(QueryWrapper qw, Long fromTime, Long toTime) {
        if (fromTime != null && fromTime > 0) {
            qw.and(ImMessage::getCreateTime).ge(new java.util.Date(fromTime));
        }
        if (toTime != null && toTime > 0) {
            qw.and(ImMessage::getCreateTime).le(new java.util.Date(toTime));
        }
    }

    /** 生成安全高亮片段：正文 HTML 转义后，关键词包在 &lt;mark&gt; 中。 */
    public static String buildSearchHighlight(String content, String keyword) {
        if (content == null || content.isBlank() || keyword == null || keyword.isBlank()) {
            return content == null ? null : org.springframework.web.util.HtmlUtils.htmlEscape(content);
        }
        String escaped = org.springframework.web.util.HtmlUtils.htmlEscape(content);
        String escKw = org.springframework.web.util.HtmlUtils.htmlEscape(keyword);
        if (escKw.isEmpty()) {
            return escaped;
        }
        StringBuilder out = new StringBuilder();
        String lower = escaped.toLowerCase(java.util.Locale.ROOT);
        String lowerKw = escKw.toLowerCase(java.util.Locale.ROOT);
        int from = 0;
        int idx;
        while ((idx = lower.indexOf(lowerKw, from)) >= 0) {
            out.append(escaped, from, idx);
            out.append("<mark>").append(escaped, idx, idx + escKw.length()).append("</mark>");
            from = idx + escKw.length();
        }
        out.append(escaped.substring(from));
        return out.toString();
    }

    @Override
    public void assertConversationMember(Long userId, Long conversationId) {
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "无权访问该会话");
        }
    }

    @Override
    @Transactional
    public long markAsRead(Long userId, Long conversationId, Long lastReadMessageId) {
        assertConversationMember(userId, conversationId);
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            return 0;
        }
        Long current = member.getLastReadMessageId();
        // 并发安全：取 max(current, lastReadMessageId) 作为已读位
        // 注：此处为 check-then-set，极端并发下可能丢失一次更新，但已读回执非资金类操作，影响可控
        Long updated = lastReadMessageId == null ? current : (current == null ? lastReadMessageId : Math.max(current, lastReadMessageId));
        if (updated != null && !updated.equals(current)) {
            member.setLastReadMessageId(updated);
            memberMapper.update(member);
        }
        return calcUnread(userId, conversationId, updated);
    }

    @Override
    public long getUnreadCount(Long userId, Long conversationId) {
        assertConversationMember(userId, conversationId);
        Long lastRead = loadLastReadMessageId(userId, conversationId);
        return calcUnread(userId, conversationId, lastRead);
    }

    @Override
    public long getTotalUnreadCount(Long userId) {
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (ImConversationMember m : memberships) {
            Long lastRead = loadLastReadMessageId(userId, m.getConversationId());
            total += calcUnread(userId, m.getConversationId(), lastRead);
        }
        return total;
    }

    private long calcUnread(Long userId, Long conversationId, Long lastReadMessageId) {
        QueryWrapper qw = QueryWrapper.create()
                .where(ImMessage::getConversationId).eq(conversationId)
                .and(ImMessage::getSenderId).ne(userId)
                .and(ImMessage::getType).ne(ImMessage.TYPE_RECALL)
                .and(ImMessage::getType).ne(ImMessage.TYPE_SYSTEM);
        if (lastReadMessageId != null) {
            qw.and(ImMessage::getId).gt(lastReadMessageId);
        }
        return messageMapper.selectCountByQuery(qw);
    }

    private void refreshConversationLastMessage(Long conversationId) {
        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            return;
        }
        ImMessage latest = messageMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getConversationId).eq(conversationId)
                        .orderBy(ImMessage::getCreateTime, false)
                        .limit(1)
        );
        if (latest == null) {
            conversation.setLastMessageContent("");
            conversation.setLastMessageTime(null);
        } else {
            conversation.setLastMessageContent(buildPreview(latest));
            conversation.setLastMessageTime(latest.getCreateTime());
        }
        conversationMapper.update(conversation);
    }

    private String buildClientMsgDedupKey(Long userId, String clientMsgId) {
        return "linkx:msg:dedup:" + userId + ":" + clientMsgId;
    }

    /**
     * 事务回滚时补偿删除 Redis 去重键。
     * <p>
     * 去重键在事务内、DB 操作之前写入 Redis，若事务回滚后键仍存活（TTL 10 分钟），
     * 会导致同一 client_msg_id 的合法重试被误判为重复而阻塞。
     * 通过 afterCompletion(STATUS_ROLLED_BACK) 在回滚后删除键，恢复重试能力。
     * </p>
     */
    private void registerDedupKeyRollbackCleanup(String dedupKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        redisTemplate.delete(dedupKey);
                    } catch (Exception ignored) {
                        // 补偿删除失败不影响主流程；键有 TTL 会自动过期
                    }
                }
            }
        });
    }

    private void assertGroupSpeakAllowed(Long userId, ImConversation group) {
        Date now = new Date();
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(group.getId())
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "你不是该群成员");
        }

        boolean isPrivileged = group.getOwnerId() != null && group.getOwnerId().equals(userId)
                || ImConversationMember.ROLE_OWNER.equals(member.getRole())
                || ImConversationMember.ROLE_ADMIN.equals(member.getRole());

        if (GroupServiceImpl.isMuteAllActive(group, now) && !isPrivileged) {
            throw new CustomException(403, "全员禁言中，仅群主和管理员可发言");
        }
        if (GroupServiceImpl.isMemberMuteActive(member, now)) {
            throw new CustomException(403, "你已被禁言，暂时无法发言");
        }
    }

    /**
     * 超大群消息风暴控制。
     * <p>
     * 对 500+ 人以上的大群实施每用户消息频率限制，防止消息风暴影响系统稳定性。
     * - 500-1000 人群：每用户每分钟最多 10 条
     * - 1000+ 人群：每用户每分钟最多 5 条
     * 超限事件落库 {@code im_message_storm_event}。
     * </p>
     */
    private void checkGroupMessageStormLimit(Long userId, Long conversationId) {
        String stormKey = "linkx:storm:" + conversationId;
        String countStr = redisTemplate.opsForValue().get(stormKey + ":count");
        int memberCount = countStr != null ? Integer.parseInt(countStr) : (int) getMemberCount(conversationId);

        if (countStr == null) {
            redisTemplate.opsForValue().set(stormKey + ":count", String.valueOf(memberCount),
                    Duration.ofSeconds(60));
        }

        messageStormService.checkAndRecordGroupStorm(userId, conversationId, memberCount);
    }

    private int getMemberCountInternal(Long conversationId) {
        return (int) memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getDeleted).eq(0)
        );
    }

    private void ensurePrivateMembership(Long conversationId, Long userId) {
        ImConversationMember active = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (active != null) {
            return;
        }
        ImConversationMember existing = LogicDeleteManager.execWithoutLogicDelete(() ->
                memberMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImConversationMember::getConversationId).eq(conversationId)
                                .and(ImConversationMember::getUserId).eq(userId)
                                .limit(1)
                )
        );
        if (existing != null) {
            existing.setDeleted(0);
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                memberMapper.update(existing);
                return null;
            });
            return;
        }
        memberMapper.insert(ImConversationMember.builder()
                .conversationId(conversationId)
                .userId(userId)
                .deleted(0)
                .build());
    }

    /** 是否已屏蔽对方，或被对方屏蔽 */
    private boolean isBlockedEitherWay(Long userId, Long peerId) {
        if (peerId == null) return false;
        return sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(peerId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_BLOCKED)
        ) > 0
                || sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(peerId)
                        .and(SysUserRelation::getFriendId).eq(userId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_BLOCKED)
        ) > 0;
    }

    private Map<Long, SysUserRelation> loadRelationMap(Long userId, Set<Long> friendIds) {
        if (friendIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).in(friendIds)
        );
        Map<Long, SysUserRelation> map = new HashMap<>();
        for (SysUserRelation relation : relations) {
            map.put(relation.getFriendId(), relation);
        }
        return map;
    }

    private boolean isFriend(Long userId, Long friendId) {
        return sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
        ) > 0;
    }

    @Override
    public List<Long> listPrivatePeerIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<Long> conversationIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());
        List<ImConversation> privates = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getId).in(conversationIds)
                        .and(ImConversation::getType).eq(ImConversation.TYPE_PRIVATE)
        );
        if (privates.isEmpty()) {
            return List.of();
        }
        Set<Long> privateConvIds = privates.stream()
                .map(ImConversation::getId)
                .collect(Collectors.toSet());
        List<ImConversationMember> peers = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).in(privateConvIds)
                        .and(ImConversationMember::getUserId).ne(userId)
        );
        return peers.stream()
                .map(ImConversationMember::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void assertCanPrivateChat(Long userId, Long peerId) {
        if (isFriend(userId, peerId)) {
            return;
        }
        if (userPreferenceService.allowsStrangerChat(peerId)
                || userPreferenceService.allowsStrangerChat(userId)) {
            return;
        }
        throw new CustomException(403, "只能与好友聊天，或对方需开启允许陌生人会话");
    }

    private boolean resolvePeerOnline(Long peerUserId) {
        if (peerUserId == null) return false;
        if (!userPreferenceService.showsOnlineStatus(peerUserId)) {
            return false;
        }
        return presenceService.isOnline(peerUserId);
    }

    private Long resolvePrivatePeerId(Long userId, Long conversationId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        return members.stream()
                .map(ImConversationMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(404, "会话成员不存在"));
    }

    private Map<Long, SysUser> loadPeerUsers(Long userId, List<ImConversation> conversations) {
        // 收集所有私聊会话 ID
        List<Long> privateConversationIds = conversations.stream()
                .filter(c -> c.getType() == ImConversation.TYPE_PRIVATE)
                .map(ImConversation::getId)
                .collect(Collectors.toList());

        if (privateConversationIds.isEmpty()) {
            return Map.of();
        }

        // 批量查询私聊会话的所有成员
        List<ImConversationMember> allMembers = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).in(privateConversationIds)
        );

        // 按会话 ID 分组
        Map<Long, List<ImConversationMember>> conversationMembersMap = allMembers.stream()
                .collect(Collectors.groupingBy(ImConversationMember::getConversationId));

        // 收集所有 peer ID（排除当前用户）
        List<Long> peerIds = new ArrayList<>();
        for (ImConversation conversation : conversations) {
            if (conversation.getType() != ImConversation.TYPE_PRIVATE) {
                continue;
            }
            List<ImConversationMember> members = conversationMembersMap.get(conversation.getId());
            if (members != null) {
                for (ImConversationMember member : members) {
                    if (!member.getUserId().equals(userId)) {
                        peerIds.add(member.getUserId());
                        break;
                    }
                }
            }
        }

        if (peerIds.isEmpty()) {
            return Map.of();
        }

        // 批量查询 peer 用户，避免 N+1
        List<SysUser> peers = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(peerIds)
        );

        // 建立 conversationId -> peer 用户 的映射
        Map<Long, SysUser> result = new HashMap<>();
        for (ImConversation conversation : conversations) {
            if (conversation.getType() != ImConversation.TYPE_PRIVATE) {
                continue;
            }
            List<ImConversationMember> members = conversationMembersMap.get(conversation.getId());
            if (members != null) {
                for (ImConversationMember member : members) {
                    if (!member.getUserId().equals(userId)) {
                        SysUser peer = peers.stream()
                                .filter(u -> u.getId().equals(member.getUserId()))
                                .findFirst()
                                .orElse(null);
                        if (peer != null) {
                            result.put(conversation.getId(), peer);
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }

    private Map<Long, String> loadRemarkMap(Long userId, Set<Long> friendIds) {
        if (friendIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).in(friendIds)
        );
        Map<Long, String> remarkMap = new HashMap<>();
        for (SysUserRelation relation : relations) {
            remarkMap.put(relation.getFriendId(), relation.getRemark());
        }
        return remarkMap;
    }

    private ConversationVO toConversationVO(
            ImConversation conversation,
            SysUser peer,
            String remark,
            boolean peerOnline,
            long unreadCount,
            boolean pinned,
            boolean important,
            boolean muted,
            boolean blocked
    ) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .peerUserId(peer.getId())
                .peerUsername(peer.getUsername())
                .peerNickname(peer.getNickname())
                .peerAvatar(mediaUrlService.resolve(peer.getAvatar()))
                .peerRemark(remark)
                .peerOnline(peerOnline)
                .lastMessage(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime() != null
                        ? conversation.getLastMessageTime().getTime()
                        : null)
                .unreadCount(unreadCount)
                .pinned(pinned)
                .important(important)
                .muted(muted)
                .blocked(blocked)
                .build();
    }

    private ConversationVO toGroupConversationVO(
            ImConversation conversation,
            List<GroupMemberAvatarVO> memberAvatars,
            String myRemark,
            long unreadCount,
            boolean pinned,
            boolean important,
            boolean muted
    ) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .myRemark(myRemark)
                .avatar(mediaUrlService.resolve(conversation.getAvatar()))
                .peerAvatar(mediaUrlService.resolve(conversation.getAvatar()))
                .memberAvatars(memberAvatars)
                .announcement(conversation.getAnnouncement())
                .ownerId(conversation.getOwnerId())
                .lastMessage(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime() != null
                        ? conversation.getLastMessageTime().getTime()
                        : null)
                .unreadCount(unreadCount)
                .pinned(pinned)
                .important(important)
                .muted(muted)
                .build();
    }

    private boolean isPinned(Map<Long, ImConversationMember> membershipMap, Long conversationId) {
        ImConversationMember m = membershipMap.get(conversationId);
        return m != null && m.getPinned() != null && m.getPinned() == 1;
    }

    private boolean isImportant(Map<Long, ImConversationMember> membershipMap, Long conversationId) {
        ImConversationMember m = membershipMap.get(conversationId);
        return m != null && m.getImportant() != null && m.getImportant() == 1;
    }

    private Map<Long, String> loadGroupRemarkMap(Long userId, Set<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).in(groupIds)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        Map<Long, String> map = new HashMap<>();
        for (ImConversationMember m : memberships) {
            if (m.getRemark() != null && !m.getRemark().isBlank()) {
                map.put(m.getConversationId(), m.getRemark());
            }
        }
        return map;
    }

    private Map<Long, List<GroupMemberAvatarVO>> loadGroupMemberAvatarPreviews(Set<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).in(groupIds)
        );
        if (memberships.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = memberships.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        Map<Long, List<ImConversationMember>> byConv = memberships.stream()
                .collect(Collectors.groupingBy(ImConversationMember::getConversationId));

        Map<Long, List<GroupMemberAvatarVO>> result = new HashMap<>();
        for (Map.Entry<Long, List<ImConversationMember>> entry : byConv.entrySet()) {
            List<ImConversationMember> sorted = entry.getValue().stream()
                    .sorted(Comparator
                            .comparingInt((ImConversationMember m) -> roleRank(m.getRole()))
                            .thenComparing(m -> m.getCreateTime() != null ? m.getCreateTime().getTime() : 0L))
                    .limit(9)
                    .toList();
            List<GroupMemberAvatarVO> previews = new ArrayList<>(sorted.size());
            for (ImConversationMember m : sorted) {
                SysUser user = userMap.get(m.getUserId());
                if (user == null) {
                    continue;
                }
                String nick = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
                previews.add(GroupMemberAvatarVO.builder()
                        .nickname(nick)
                        .avatar(mediaUrlService.resolve(user.getAvatar()))
                        .build());
            }
            result.put(entry.getKey(), previews);
        }
        return result;
    }

    private static int roleRank(String role) {
        if (ImConversationMember.ROLE_OWNER.equals(role)) return 0;
        if (ImConversationMember.ROLE_ADMIN.equals(role)) return 1;
        return 2;
    }

    private MessageVO toMessageVO(ImMessage message, SysUser sender, Long currentUserId, Long lastReadMessageId) {
        return toMessageVO(message, sender, currentUserId, lastReadMessageId, null, null);
    }

    private MessageVO toMessageVO(ImMessage message, SysUser sender, Long currentUserId, Long lastReadMessageId,
                                  Map<Long, RedPacket> redPacketCache, Map<Long, RedPacketRecord> recordCache) {
        String fileUrl = message.getFileUrl();
        if (fileUrl != null
                && !ImMessage.TYPE_RED_PACKET.equals(message.getType())
                && !ImMessage.TYPE_CONFERENCE.equals(message.getType())) {
            fileUrl = mediaUrlService.resolveFile(fileUrl);
        }
        MessageVO.MessageVOBuilder builder = MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderNickname(sender != null ? sender.getNickname() : null)
                .senderAvatar(sender != null ? mediaUrlService.resolve(sender.getAvatar()) : null)
                .type(message.getType())
                .content(message.getContent())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileUrl(fileUrl)
                .voiceDuration(message.getVoiceDuration())
                .createTime(message.getCreateTime() != null ? message.getCreateTime().getTime() : null)
                .isSelf(message.getSenderId().equals(currentUserId))
                .clientMsgId(message.getClientMsgId())
                .deliveryStatus(message.getDeliveryStatus())
                .readStatus(isRead(message, currentUserId, lastReadMessageId))
                .unreadCount(calcPerMessageUnread(message, currentUserId, lastReadMessageId))
                .edited(Boolean.TRUE.equals(message.getEdited()))
                .editedTime(message.getEditedTime() != null ? message.getEditedTime().getTime() : null)
                .forwardFromMessageId(message.getForwardFromMessageId())
                .forwardFromConversationId(message.getForwardFromConversationId())
                .quoteMessageId(message.getQuoteMessageId())
                .quoteConversationId(message.getQuoteConversationId())
                .quoteSenderId(message.getQuoteSenderId())
                .quoteContent(message.getQuoteContent())
                .quoteType(message.getQuoteType());

        if (ImMessage.TYPE_RED_PACKET.equals(message.getType()) && message.getFileUrl() != null) {
            fillRedPacketFields(builder, message, currentUserId, redPacketCache, recordCache);
        }

        return builder.build();
    }

    private void fillRedPacketFields(MessageVO.MessageVOBuilder builder, ImMessage message, Long currentUserId) {
        fillRedPacketFields(builder, message, currentUserId, null, null);
    }

    private void fillRedPacketFields(MessageVO.MessageVOBuilder builder, ImMessage message, Long currentUserId,
                                     Map<Long, RedPacket> redPacketCache, Map<Long, RedPacketRecord> recordCache) {
        Long redPacketId;
        try {
            redPacketId = Long.parseLong(message.getFileUrl());
        } catch (NumberFormatException e) {
            return;
        }
        RedPacket redPacket = redPacketCache != null
                ? redPacketCache.get(redPacketId)
                : redPacketMapper.selectOneById(redPacketId);
        if (redPacket == null) {
            return;
        }
        // 防止消息 fileUrl 指向其他会话红包导致金额信息串会话泄露
        if (message.getConversationId() != null
                && !message.getConversationId().equals(redPacket.getConversationId())) {
            return;
        }
        BigDecimal totalYuan = redPacket.getTotalAmount();
        RedPacketRecord userRecord = null;
        if (currentUserId != null) {
            if (recordCache != null) {
                userRecord = recordCache.get(redPacketId);
            } else {
                userRecord = redPacketRecordMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .eq("red_packet_id", redPacketId)
                                .eq("user_id", currentUserId)
                );
            }
        }
        builder
                .redPacketId(String.valueOf(redPacketId))
                .redPacketGreeting(redPacket.getGreeting())
                .redPacketTotalAmount(totalYuan)
                .redPacketType(redPacket.getType())
                .redPacketTotalCount(redPacket.getTotalCount())
                .redPacketRemainingCount(redPacket.getRemainingCount())
                .redPacketReceived(userRecord != null)
                .redPacketReceivedAmount(userRecord != null ? userRecord.getAmount() : null)
                .redPacketStatus(redPacket.getStatus());
    }

    /** 批量加载消息列表中的红包，避免 listMessages N+1 */
    private Map<Long, RedPacket> loadRedPacketsForMessages(List<ImMessage> messages) {
        Set<Long> ids = new HashSet<>();
        for (ImMessage message : messages) {
            if (!ImMessage.TYPE_RED_PACKET.equals(message.getType()) || message.getFileUrl() == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(message.getFileUrl()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return redPacketMapper.selectListByQuery(
                QueryWrapper.create().where(RedPacket::getId).in(ids)
        ).stream().collect(Collectors.toMap(RedPacket::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, RedPacketRecord> loadMyRedPacketRecords(Long userId, Set<Long> redPacketIds) {
        if (userId == null || redPacketIds == null || redPacketIds.isEmpty()) {
            return Map.of();
        }
        return redPacketRecordMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .in("red_packet_id", redPacketIds)
        ).stream().collect(Collectors.toMap(RedPacketRecord::getRedPacketId, Function.identity(), (a, b) -> a));
    }

    private Long loadLastReadMessageId(Long userId, Long conversationId) {
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        return member != null ? member.getLastReadMessageId() : null;
    }

    private Integer isRead(ImMessage message, Long currentUserId, Long lastReadMessageId) {
        if (message.getSenderId().equals(currentUserId)) {
            return 1;
        }
        return lastReadMessageId != null && message.getId() <= lastReadMessageId ? 1 : 0;
    }

    private Long calcPerMessageUnread(ImMessage message, Long currentUserId, Long lastReadMessageId) {
        if (message.getSenderId().equals(currentUserId)) {
            return 0L;
        }
        return lastReadMessageId != null && message.getId() <= lastReadMessageId ? 0L : 1L;
    }

    private String buildPrivateKey(Long userId, Long friendId) {
        long min = Math.min(userId, friendId);
        long max = Math.max(userId, friendId);
        return min + "_" + max;
    }

    /**
     * 解析消息类型。红包仅允许在「同会话、同发送者、已存在红包实体」时落库，
     * 阻止客户端 WS/HTTP 伪造 redPacket 气泡或串会话展示金额。
     */
    private String resolveMsgType(Long userId, SendMessageDTO dto) {
        if (!StringUtils.hasText(dto.getMsgType())) {
            throw new CustomException(400, "消息类型不能为空");
        }
        String raw = dto.getMsgType().trim();
        if (ImMessage.TYPE_RED_PACKET.equalsIgnoreCase(raw) || "redpacket".equalsIgnoreCase(raw)) {
            assertLegitimateRedPacketMessage(userId, dto);
            return ImMessage.TYPE_RED_PACKET;
        }
        if (ImMessage.TYPE_CONFERENCE.equalsIgnoreCase(raw)
                || ImMessage.TYPE_SYSTEM.equalsIgnoreCase(raw)
                || ImMessage.TYPE_RECALL.equalsIgnoreCase(raw)) {
            throw new CustomException(400, "不支持的消息类型");
        }
        return normalizeMsgType(raw);
    }

    private void assertLegitimateRedPacketMessage(Long userId, SendMessageDTO dto) {
        if (!StringUtils.hasText(dto.getFileUrl())) {
            throw new CustomException(400, "红包消息缺少红包 ID");
        }
        Long packetId;
        try {
            packetId = Long.parseLong(dto.getFileUrl().trim());
        } catch (NumberFormatException e) {
            throw new CustomException(400, "无效的红包 ID");
        }
        RedPacket redPacket = redPacketMapper.selectOneById(packetId);
        if (redPacket == null) {
            throw new CustomException(400, "红包不存在，无法发送红包消息");
        }
        if (!userId.equals(redPacket.getSenderId())) {
            throw new CustomException(403, "无权发送该红包消息");
        }
        if (!dto.getConversationId().equals(redPacket.getConversationId())) {
            throw new CustomException(403, "红包与会话不匹配");
        }
    }

    private String normalizeMsgType(String msgType) {
        if (!StringUtils.hasText(msgType)) {
            throw new CustomException(400, "消息类型不能为空");
        }
        String type = msgType.trim().toLowerCase();
        if (!ImMessage.TYPE_TEXT.equals(type)
                && !ImMessage.TYPE_IMAGE.equals(type)
                && !ImMessage.TYPE_FILE.equals(type)
                && !ImMessage.TYPE_VOICE.equals(type)
                && !ImMessage.TYPE_LOCATION.equals(type)) {
            throw new CustomException(400, "不支持的消息类型");
        }
        return type;
    }

    private void validateMessagePayload(String msgType, SendMessageDTO dto) {
        if (ImMessage.TYPE_TEXT.equals(msgType) || ImMessage.TYPE_LOCATION.equals(msgType)) {
            if (!StringUtils.hasText(dto.getContent())) {
                throw new CustomException(400, ImMessage.TYPE_LOCATION.equals(msgType) ? "位置不能为空" : "文本消息不能为空");
            }
            return;
        }
        if (!StringUtils.hasText(dto.getFileUrl())) {
            throw new CustomException(400, "文件 URL 不能为空");
        }
        if (ImMessage.TYPE_FILE.equals(msgType) && !StringUtils.hasText(dto.getFileName())) {
            throw new CustomException(400, "文件名不能为空");
        }
        if (ImMessage.TYPE_VOICE.equals(msgType)
                && (dto.getVoiceDuration() == null || dto.getVoiceDuration() <= 0)) {
            throw new CustomException(400, "语音时长无效");
        }
    }

    private String resolveContent(String msgType, SendMessageDTO dto, String storedFileUrl) {
        if (ImMessage.TYPE_TEXT.equals(msgType) || ImMessage.TYPE_LOCATION.equals(msgType)) {
            return InputSanitizer.sanitizeText(dto.getContent(), 4000);
        }
        if (ImMessage.TYPE_IMAGE.equals(msgType)) {
            // 图片 content 与 fileUrl 统一存 object key，列表时再签发
            return StringUtils.hasText(storedFileUrl) ? storedFileUrl : dto.getFileUrl();
        }
        return StringUtils.hasText(dto.getContent())
                ? InputSanitizer.sanitizeText(dto.getContent(), 500)
                : dto.getFileName();
    }

    /**
     * 将客户端传入的 key / 预签名 URL 规范为 object key，且须属主认领。
     */
    private String normalizeAndAuthorizeMediaKey(Long userId, String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new CustomException(400, "文件 URL 不能为空");
        }
        String trimmed = raw.trim();
        if (mediaUrlService.isExternalHttpUrl(trimmed)) {
            throw new CustomException(400, "聊天附件不支持外部链接，请先上传");
        }
        String key = fileStorageService.extractObjectKey(trimmed);
        if (!StringUtils.hasText(key)
                || key.contains("..")
                || key.startsWith("/")
                || key.contains("://")) {
            throw new CustomException(400, "无效的文件引用");
        }
        objectKeyOwnershipService.assertOwned(userId, key);
        return key;
    }

    private String buildPreview(ImMessage message) {
        return switch (message.getType()) {
            case ImMessage.TYPE_IMAGE -> "[图片]";
            case ImMessage.TYPE_FILE -> "[文件] " + (message.getFileName() != null ? message.getFileName() : "文件");
            case ImMessage.TYPE_VOICE -> "[语音]";
            case ImMessage.TYPE_LOCATION -> "[位置] " + (message.getContent() != null ? message.getContent() : "");
            case ImMessage.TYPE_RED_PACKET -> "[红包] " + (message.getFileName() != null ? message.getFileName() : "恭喜发财");
            case ImMessage.TYPE_CONFERENCE -> {
                String c = message.getContent();
                if (c != null && c.contains("语音通话")) {
                    yield "[语音通话] " + (message.getFileName() != null ? message.getFileName() : "语音通话");
                }
                if (c != null && c.contains("视频通话")) {
                    yield "[视频通话] " + (message.getFileName() != null ? message.getFileName() : "视频通话");
                }
                yield "[会议] " + (message.getFileName() != null ? message.getFileName() : "多人会议");
            }
            case ImMessage.TYPE_RECALL -> "撤回了一条消息";
            case ImMessage.TYPE_SYSTEM -> message.getContent() != null ? message.getContent() : "[系统消息]";
            default -> message.getContent();
        };
    }

    /** 编辑窗口：24 小时内可编辑 */
    private static final long EDIT_WINDOW_MS = 24 * 60 * 60 * 1000L;

    @Override
    @Transactional
    public MessageVO editMessage(Long userId, Long conversationId, Long messageId, String newContent) {
        assertConversationMember(userId, conversationId);

        ImMessage message = messageMapper.selectOneById(messageId);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw new CustomException(404, "消息不存在");
        }
        if (!userId.equals(message.getSenderId())) {
            throw new CustomException(403, "只能编辑自己发送的消息");
        }
        if (!ImMessage.TYPE_TEXT.equals(message.getType())) {
            throw new CustomException(400, "只能编辑文本消息");
        }
        if (ImMessage.TYPE_RECALL.equals(message.getType())) {
            throw new CustomException(400, "已撤回的消息不能编辑");
        }

        Date createTime = message.getCreateTime();
        if (createTime == null || System.currentTimeMillis() - createTime.getTime() > EDIT_WINDOW_MS) {
            throw new CustomException(400, "超过编辑时限（24小时）");
        }

        String sanitized = InputSanitizer.sanitizeText(newContent.trim(), 4000);
        if (sanitized.isBlank()) {
            throw new CustomException(400, "编辑内容不能为空");
        }

        // 敏感词过滤
        String originalContent = sanitized;
        SensitiveWordService.FilterResult filterResult = sensitiveWordService.filter(sanitized);
        if (!filterResult.matchedWords().isEmpty()) {
            String failReason = filterResult.blocked()
                    ? "blocked"
                    : (filterResult.filtered() ? "filtered" : (filterResult.alerted() ? "alert" : "matched"));
            String matchedWords = String.join(",", filterResult.matchedWords());
            auditLogService.log(
                    SysAuditLog.OperationType.SENSITIVE_WORD_MATCH,
                    "敏感词命中(编辑): " + matchedWords,
                    userId,
                    null,
                    null,
                    null,
                    !filterResult.blocked(),
                    failReason
            );
            adminRiskEventService.recordSensitiveMatch(userId, matchedWords, failReason, conversationId);
            if (filterResult.blocked()) {
                enqueueSensitiveReview(
                        userId,
                        SysReviewTask.TARGET_MESSAGE,
                        String.valueOf(messageId),
                        conversationId,
                        originalContent,
                        matchedWords,
                        "blocked");
                throw new CustomException(400, "编辑内容包含违禁内容，无法保存");
            }
            enqueueSensitiveReview(
                    userId,
                    SysReviewTask.TARGET_MESSAGE,
                    String.valueOf(messageId),
                    conversationId,
                    originalContent,
                    matchedWords,
                    failReason);
        }
        sanitized = filterResult.text();

        message.setContent(sanitized);
        message.setEdited(true);
        message.setEditedTime(new Date());
        messageMapper.update(message);

        // 刷新会话预览
        refreshConversationLastMessage(conversationId);

        SysUser sender = sysUserMapper.selectOneById(userId);
        MessageVO vo = toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversationId));
        if (filterResult.alerted()) {
            vo.setSensitiveAlert(Boolean.TRUE);
        }
        return vo;
    }

    @Override
    @Transactional
    public MessageVO forwardMessage(Long userId, Long sourceConversationId, Long sourceMessageId, Long targetConversationId) {
        assertConversationMember(userId, sourceConversationId);
        assertConversationMember(userId, targetConversationId);

        ImMessage source = messageMapper.selectOneById(sourceMessageId);
        if (source == null || !sourceConversationId.equals(source.getConversationId())) {
            throw new CustomException(404, "源消息不存在");
        }
        if (ImMessage.TYPE_RECALL.equals(source.getType())) {
            throw new CustomException(400, "不能转发已撤回的消息");
        }
        if (ImMessage.TYPE_SYSTEM.equals(source.getType())) {
            throw new CustomException(400, "不能转发系统消息");
        }
        if (ImMessage.TYPE_CONFERENCE.equals(source.getType())) {
            throw new CustomException(400, "不能转发会议邀请");
        }
        if (ImMessage.TYPE_RED_PACKET.equals(source.getType())) {
            throw new CustomException(400, "不能转发红包消息");
        }

        String fileUrl = source.getFileUrl();
        String content = source.getContent();
        // 媒体转发：同桶复制为新对象并由转发者 claim，关闭「复用他人 key 跳过属主」旁路
        if (isForwardableMediaType(source.getType()) && StringUtils.hasText(fileUrl)) {
            String trimmed = fileUrl.trim();
            if (mediaUrlService.isExternalHttpUrl(trimmed)) {
                throw new CustomException(400, "不能转发外部链接附件");
            }
            String sourceKey = fileStorageService.extractObjectKey(trimmed);
            if (!StringUtils.hasText(sourceKey)
                    || sourceKey.contains("..")
                    || sourceKey.startsWith("/")
                    || sourceKey.contains("://")) {
                throw new CustomException(400, "源附件引用无效，无法转发");
            }
            try {
                String newKey = fileStorageService.copyObject(sourceKey, source.getFileName());
                objectKeyOwnershipService.claim(userId, newKey);
                fileUrl = newKey;
                if (ImMessage.TYPE_IMAGE.equals(source.getType())) {
                    content = newKey;
                }
            } catch (IllegalArgumentException e) {
                throw new CustomException(400, e.getMessage() != null ? e.getMessage() : "源附件无法转发");
            } catch (RuntimeException e) {
                throw new CustomException(500, "转发附件失败");
            }
        }

        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(targetConversationId);
        dto.setMsgType(source.getType());
        dto.setContent(content);
        dto.setFileName(source.getFileName());
        dto.setFileSize(source.getFileSize());
        dto.setFileUrl(fileUrl);
        dto.setVoiceDuration(source.getVoiceDuration());
        dto.setClientMsgId("fwd-" + UUID.randomUUID().toString());

        MessageVO sent = sendMessage(userId, dto);

        // 标记转发来源
        ImMessage forwarded = messageMapper.selectOneById(sent.getId());
        if (forwarded != null) {
            forwarded.setForwardFromMessageId(sourceMessageId);
            forwarded.setForwardFromConversationId(sourceConversationId);
            messageMapper.update(forwarded);
        }

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(forwarded != null ? forwarded : messageMapper.selectOneById(sent.getId()),
                sender, userId, loadLastReadMessageId(userId, targetConversationId));
    }

    private static boolean isForwardableMediaType(String type) {
        return ImMessage.TYPE_IMAGE.equals(type)
                || ImMessage.TYPE_FILE.equals(type)
                || ImMessage.TYPE_VOICE.equals(type);
    }

    @Override
    @Transactional
    public MessageVO quoteMessage(Long userId, Long conversationId, Long quoteMessageId, SendMessageDTO dto) {
        assertConversationMember(userId, conversationId);

        ImMessage quoted = messageMapper.selectOneById(quoteMessageId);
        if (quoted == null || !conversationId.equals(quoted.getConversationId())) {
            throw new CustomException(404, "引用的消息不存在");
        }
        if (ImMessage.TYPE_RECALL.equals(quoted.getType())) {
            throw new CustomException(400, "不能引用已撤回的消息");
        }
        if (ImMessage.TYPE_SYSTEM.equals(quoted.getType())) {
            throw new CustomException(400, "不能引用系统消息");
        }
        if (ImMessage.TYPE_CONFERENCE.equals(quoted.getType())) {
            throw new CustomException(400, "不能引用会议邀请");
        }
        if (ImMessage.TYPE_RED_PACKET.equals(quoted.getType())) {
            throw new CustomException(400, "不能引用红包消息");
        }

        dto.setConversationId(conversationId);
        MessageVO sent = sendMessage(userId, dto);

        // 标记引用来源
        ImMessage msg = messageMapper.selectOneById(sent.getId());
        if (msg != null) {
            msg.setQuoteMessageId(quoteMessageId);
            msg.setQuoteConversationId(conversationId);
            msg.setQuoteSenderId(quoted.getSenderId());
            msg.setQuoteContent(quoted.getContent());
            msg.setQuoteType(quoted.getType());
            messageMapper.update(msg);
        }

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(msg != null ? msg : messageMapper.selectOneById(sent.getId()),
                sender, userId, loadLastReadMessageId(userId, conversationId));
    }

    @Override
    @Transactional
    public void togglePinConversation(Long userId, Long conversationId) {
        assertConversationMember(userId, conversationId);

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "非会话成员");
        }

        member.setPinned(member.getPinned() != null && member.getPinned() == 1 ? 0 : 1);
        memberMapper.update(member);
    }

    @Override
    @Transactional
    public void toggleImportantConversation(Long userId, Long conversationId) {
        assertConversationMember(userId, conversationId);

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "非会话成员");
        }

        member.setImportant(member.getImportant() != null && member.getImportant() == 1 ? 0 : 1);
        memberMapper.update(member);
    }

    @Override
    @Transactional
    public void toggleMuteConversation(Long userId, Long conversationId) {
        assertConversationMember(userId, conversationId);

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "非会话成员");
        }

        member.setMuted(member.getMuted() != null && member.getMuted() == 1 ? 0 : 1);
        memberMapper.update(member);
    }

    @Override
    public long getMemberCount(Long conversationId) {
        return getMemberCountInternal(conversationId);
    }

    // ==================== 分片上传（断点续传） ====================

    @Override
    public java.util.Map<String, Object> initiateMultipartUpload(Long userId, Long conversationId, String fileName, String contentType, Long fileSize) {
        assertConversationMember(userId, conversationId);
        if (fileSize != null && fileSize > linkxProperties.getMinio().getMaxFileSize()) {
            throw new CustomException(400, "文件大小超过限制");
        }
        try {
            String objectName = fileStorageService.allocateObjectName(fileName);
            var session = fileStorageService.initiateMultipartUpload(objectName, contentType);
            bindMultipartInitiator(userId, conversationId, session.uploadId());
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("uploadId", session.uploadId());
            result.put("objectName", session.objectName());
            result.put("partSize", ChatService.MULTIPART_PART_SIZE);
            result.put("uploadedParts", java.util.List.of());
            return result;
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(400, e.getMessage() != null ? e.getMessage() : "初始化分片上传失败");
        }
    }

    @Override
    public String uploadPart(Long userId, Long conversationId, String objectName, String uploadId, int partNumber, MultipartFile file) {
        assertConversationMember(userId, conversationId);
        assertMultipartInitiator(userId, conversationId, uploadId);
        try {
            return fileStorageService.uploadPart(objectName, uploadId, partNumber, file.getInputStream(), file.getSize());
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (Exception e) {
            throw new CustomException(400, "分片上传失败");
        }
    }

    @Override
    public List<FileStorageService.PartETag> listUploadedParts(Long userId, Long conversationId, String uploadId) {
        assertConversationMember(userId, conversationId);
        assertMultipartInitiator(userId, conversationId, uploadId);
        try {
            return fileStorageService.listUploadedParts(uploadId);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        }
    }

    @Override
    public ChatFileUploadVO completeMultipartUpload(Long userId, Long conversationId, String objectName, String uploadId,
                                                    List<FileStorageService.PartETag> parts, String fileName, Long fileSize,
                                                    String contentType, String contentHash) {
        assertConversationMember(userId, conversationId);
        assertMultipartInitiator(userId, conversationId, uploadId);
        try {
            String finalKey = fileStorageService.completeMultipartUpload(objectName, uploadId, parts);
            objectKeyOwnershipService.claim(userId, finalKey);
            clearMultipartInitiator(uploadId);
            if (contentHash != null && contentHash.matches("(?i)^[a-f0-9]{64}$")) {
                fileStorageService.saveContentHash(contentHash, finalKey);
            }
            String signedUrl = mediaUrlService.resolveFile(finalKey);
            String name = fileName;
            if (name == null || name.isBlank()) {
                name = objectName.contains("/") ? objectName.substring(objectName.lastIndexOf('/') + 1) : objectName;
            }
            return ChatFileUploadVO.builder()
                    .url(signedUrl)
                    .fileKey(finalKey)
                    .fileName(name)
                    .fileSize(fileSize)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(400, e.getMessage() != null ? e.getMessage() : "完成分片上传失败");
        }
    }

    @Override
    public void abortMultipartUpload(Long userId, Long conversationId, String objectName, String uploadId) {
        assertConversationMember(userId, conversationId);
        assertMultipartInitiator(userId, conversationId, uploadId);
        fileStorageService.abortMultipartUpload(objectName, uploadId);
        clearMultipartInitiator(uploadId);
    }

    private void bindMultipartInitiator(Long userId, Long conversationId, String uploadId) {
        redisTemplate.opsForValue().set(
                MP_OWNER_PREFIX + uploadId,
                userId + ":" + conversationId,
                MP_OWNER_TTL);
    }

    private void assertMultipartInitiator(Long userId, Long conversationId, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new CustomException(400, "uploadId 不能为空");
        }
        String bound = redisTemplate.opsForValue().get(MP_OWNER_PREFIX + uploadId);
        if (!StringUtils.hasText(bound)) {
            throw new CustomException(404, "分片会话不存在或已过期");
        }
        if (!(userId + ":" + conversationId).equals(bound)) {
            throw new CustomException(403, "无权操作该分片上传");
        }
        redisTemplate.expire(MP_OWNER_PREFIX + uploadId, MP_OWNER_TTL);
    }

    private void clearMultipartInitiator(String uploadId) {
        if (StringUtils.hasText(uploadId)) {
            redisTemplate.delete(MP_OWNER_PREFIX + uploadId);
        }
    }

    @Override
    public String findFileByHash(Long userId, String contentHash) {
        String existingKey = fileStorageService.getObjectKeyByHashInternal(contentHash);
        if (existingKey == null) {
            return null;
        }
        // 秒传仅返回本人已 claim 的对象，避免跨用户泄露 file key
        if (!objectKeyOwnershipService.isOwned(userId, existingKey)) {
            return null;
        }
        return existingKey;
    }

    @Override
    public ChatFileUploadVO resolveFileByHash(Long userId, String contentHash, String fileName, Long fileSize, String contentType) {
        String existingKey = fileStorageService.getObjectKeyByHashInternal(contentHash);
        if (existingKey == null) {
            return null;
        }
        // 仅本人已 claim 的对象可秒传；他人同内容文件须重新上传，避免跨用户出链
        if (!objectKeyOwnershipService.isOwned(userId, existingKey)) {
            return null;
        }
        String signedUrl = mediaUrlService.resolveFile(existingKey);
        return ChatFileUploadVO.builder()
                .url(signedUrl)
                .fileKey(existingKey)
                .fileName(fileName)
                .fileSize(fileSize)
                .contentType(contentType)
                .build();
    }
}
