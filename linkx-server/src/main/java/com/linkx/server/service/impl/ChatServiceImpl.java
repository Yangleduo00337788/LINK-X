package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
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
import com.linkx.server.im.ImChannelManager;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.UserPreferenceService;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.entity.SysAuditLog;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final LinkxProperties linkxProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper redPacketRecordMapper;
    private final UserPreferenceService userPreferenceService;
    private final ImChannelManager imChannelManager;
    private final SensitiveWordService sensitiveWordService;
    private final MessageStormService messageStormService;
    private final AuditLogService auditLogService;

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
        Map<Long, SysUserRelation> relationMap = loadRelationMap(userId, peerUserMap.keySet());
        Map<Long, String> remarkMap = new HashMap<>();
        for (Map.Entry<Long, SysUserRelation> e : relationMap.entrySet()) {
            remarkMap.put(e.getKey(), e.getValue().getRemark());
        }
        Map<Long, Boolean> showOnlineMap = userPreferenceService.batchShowsOnlineStatus(
                peerUserMap.values().stream().map(SysUser::getId).collect(Collectors.toSet())
        );
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
                if (relation == null) {
                    continue;
                }
                boolean blocked = Objects.equals(relation.getStatus(), RELATION_STATUS_BLOCKED);
                if (!blocked && !Objects.equals(relation.getStatus(), RELATION_STATUS_NORMAL)) {
                    continue;
                }
                boolean showOnline = !Boolean.FALSE.equals(showOnlineMap.get(peer.getId()));
                boolean peerOnline = showOnline && imChannelManager.isOnline(peer.getId());
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
        List<MessageVO> result = new ArrayList<>();
        for (ImMessage message : messages) {
            SysUser sender = senderMap.get(message.getSenderId());
            result.add(toMessageVO(message, sender, userId, lastReadMessageId));
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
            if (!Boolean.TRUE.equals(firstTime)) {
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

        String msgType = normalizeMsgType(dto.getMsgType());
        validateMessagePayload(msgType, dto);

        // 敏感词过滤：文本消息进行 DFA 过滤
        String content = resolveContent(msgType, dto);
        if (ImMessage.TYPE_TEXT.equals(msgType) && content != null) {
            SensitiveWordService.FilterResult filterResult = sensitiveWordService.filter(content);
            if (!filterResult.matchedWords().isEmpty()) {
                auditLogService.log(
                        SysAuditLog.OperationType.SENSITIVE_WORD_MATCH,
                        "敏感词命中: " + String.join(",", filterResult.matchedWords()),
                        userId,
                        null,
                        null,
                        null,
                        !filterResult.blocked(),
                        filterResult.blocked() ? "blocked" : (filterResult.filtered() ? "filtered" : "alert")
                );
            }
            if (filterResult.blocked()) {
                throw new CustomException(400, "消息包含违禁内容，无法发送");
            }
            content = filterResult.text();
        }

        ImMessage message = ImMessage.builder()
                .conversationId(dto.getConversationId())
                .senderId(userId)
                .type(msgType)
                .content(content)
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .fileUrl(dto.getFileUrl())
                .clientMsgId(dto.getClientMsgId())
                .deliveryStatus(StringUtils.hasText(dto.getDeliveryStatus()) ? dto.getDeliveryStatus().trim() : "delivered")
                .readStatus(0)
                .voiceDuration(dto.getVoiceDuration())
                .deleted(0)
                .build();
        messageMapper.insert(message);

        Date now = message.getCreateTime() != null ? message.getCreateTime() : new Date();
        conversation.setLastMessageContent(buildPreview(message));
        conversation.setLastMessageTime(now);
        conversationMapper.update(conversation);

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversation.getId()));
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

        message.setType(ImMessage.TYPE_RECALL);
        message.setContent("");
        message.setFileName("");
        message.setFileSize(0L);
        message.setFileUrl("");
        message.setVoiceDuration(0);
        messageMapper.update(message);

        refreshConversationLastMessage(conversationId);

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversationId));
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
    public ChatFileUploadVO uploadChatFile(Long userId, Long conversationId, MultipartFile file) {
        assertConversationMember(userId, conversationId);
        try {
            String objectKey = fileStorageService.uploadFile(file, null);
            String signedUrl = mediaUrlService.resolveFile(objectKey);
            return ChatFileUploadVO.builder()
                    .url(signedUrl)
                    .fileKey(objectKey)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();
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
        return imChannelManager.isOnline(peerUserId);
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
        Map<Long, SysUser> result = new HashMap<>();
        for (ImConversation conversation : conversations) {
            if (conversation.getType() != ImConversation.TYPE_PRIVATE) {
                continue;
            }
            try {
                Long peerId = resolvePrivatePeerId(userId, conversation.getId());
                if (peerId != null) {
                    SysUser peer = sysUserMapper.selectOneById(peerId);
                    if (peer != null) {
                        result.put(conversation.getId(), peer);
                    }
                }
            } catch (Exception e) {
                continue;
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
        String fileUrl = message.getFileUrl();
        if (fileUrl != null && !ImMessage.TYPE_RED_PACKET.equals(message.getType())) {
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
            fillRedPacketFields(builder, message, currentUserId);
        }

        return builder.build();
    }

    private void fillRedPacketFields(MessageVO.MessageVOBuilder builder, ImMessage message, Long currentUserId) {
        Long redPacketId;
        try {
            redPacketId = Long.parseLong(message.getFileUrl());
        } catch (NumberFormatException e) {
            return;
        }
        RedPacket redPacket = redPacketMapper.selectOneById(redPacketId);
        if (redPacket == null) {
            return;
        }
        BigDecimal totalYuan = redPacket.getTotalAmount();
        RedPacketRecord userRecord = null;
        if (currentUserId != null) {
            userRecord = redPacketRecordMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq("red_packet_id", redPacketId)
                            .and("user_id", currentUserId)
            );
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

    private String normalizeMsgType(String msgType) {
        if (!StringUtils.hasText(msgType)) {
            throw new CustomException(400, "消息类型不能为空");
        }
        String type = msgType.trim().toLowerCase();
        if (!ImMessage.TYPE_TEXT.equals(type)
                && !ImMessage.TYPE_IMAGE.equals(type)
                && !ImMessage.TYPE_FILE.equals(type)
                && !ImMessage.TYPE_VOICE.equals(type)
                && !ImMessage.TYPE_RED_PACKET.equals(type)) {
            throw new CustomException(400, "不支持的消息类型");
        }
        return type;
    }

    private void validateMessagePayload(String msgType, SendMessageDTO dto) {
        if (ImMessage.TYPE_TEXT.equals(msgType)) {
            if (!StringUtils.hasText(dto.getContent())) {
                throw new CustomException(400, "文本消息不能为空");
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

    private String resolveContent(String msgType, SendMessageDTO dto) {
        if (ImMessage.TYPE_TEXT.equals(msgType)) {
            return InputSanitizer.sanitizeText(dto.getContent(), 4000);
        }
        if (ImMessage.TYPE_IMAGE.equals(msgType)) {
            return StringUtils.hasText(dto.getContent()) ? InputSanitizer.sanitizeText(dto.getContent(), 500) : dto.getFileUrl();
        }
        return StringUtils.hasText(dto.getContent())
                ? InputSanitizer.sanitizeText(dto.getContent(), 500)
                : dto.getFileName();
    }

    private String buildPreview(ImMessage message) {
        return switch (message.getType()) {
            case ImMessage.TYPE_IMAGE -> "[图片]";
            case ImMessage.TYPE_FILE -> "[文件] " + (message.getFileName() != null ? message.getFileName() : "文件");
            case ImMessage.TYPE_VOICE -> "[语音]";
            case ImMessage.TYPE_RED_PACKET -> "[红包] " + (message.getFileName() != null ? message.getFileName() : "恭喜发财");
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
        SensitiveWordService.FilterResult filterResult = sensitiveWordService.filter(sanitized);
        if (filterResult.blocked()) {
            throw new CustomException(400, "编辑内容包含违禁内容，无法保存");
        }
        sanitized = filterResult.text();

        message.setContent(sanitized);
        message.setEdited(true);
        message.setEditedTime(new Date());
        messageMapper.update(message);

        // 刷新会话预览
        refreshConversationLastMessage(conversationId);

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(message, sender, userId, loadLastReadMessageId(userId, conversationId));
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

        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(targetConversationId);
        dto.setMsgType(source.getType());
        dto.setContent(source.getContent());
        dto.setFileName(source.getFileName());
        dto.setFileSize(source.getFileSize());
        dto.setFileUrl(source.getFileUrl());
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
        try {
            String finalKey = fileStorageService.completeMultipartUpload(objectName, uploadId, parts);
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
        fileStorageService.abortMultipartUpload(objectName, uploadId);
    }

    @Override
    public String findFileByHash(Long userId, String contentHash) {
        return fileStorageService.findByContentHash(contentHash);
    }

    @Override
    public ChatFileUploadVO resolveFileByHash(Long userId, String contentHash, String fileName, Long fileSize, String contentType) {
        String existingKey = fileStorageService.findByContentHash(contentHash);
        if (existingKey == null) {
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
