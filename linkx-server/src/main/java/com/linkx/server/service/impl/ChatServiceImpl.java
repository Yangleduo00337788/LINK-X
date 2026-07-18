package com.linkx.server.service.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ConversationVO;
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
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int RELATION_STATUS_NORMAL = 1;

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final StringRedisTemplate redisTemplate;
    private final RedPacketMapper redPacketMapper;
    private final RedPacketRecordMapper redPacketRecordMapper;

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<Long> conversationIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());

        List<ImConversation> conversations = conversationMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversation::getId).in(conversationIds)
        );
        // 按 lastMessageTime 降序排列，null 时间排到最后（最新消息的会话排在最前）
        conversations.sort((a, b) -> {
            Date timeA = a.getLastMessageTime();
            Date timeB = b.getLastMessageTime();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;  // null 排后面
            if (timeB == null) return -1; // null 排后面
            return timeB.compareTo(timeA); // 最新的在前
        });

        Map<Long, SysUser> peerUserMap = loadPeerUsers(userId, conversations);
        Map<Long, String> remarkMap = loadRemarkMap(userId, peerUserMap.keySet());

        List<ConversationVO> result = new ArrayList<>();
        for (ImConversation conversation : conversations) {
            if (conversation.getType() == ImConversation.TYPE_PRIVATE) {
                SysUser peer = peerUserMap.get(conversation.getId());
                if (peer == null) {
                    continue;
                }
                result.add(toConversationVO(conversation, peer, remarkMap.get(peer.getId())));
            } else if (conversation.getType() == ImConversation.TYPE_GROUP) {
                result.add(toGroupConversationVO(conversation));
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
        assertFriendship(userId, friendId);

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
                    .build();
            conversationMapper.insert(conversation);

            memberMapper.insert(ImConversationMember.builder()
                    .conversationId(conversation.getId())
                    .userId(userId)
                    .build());
            memberMapper.insert(ImConversationMember.builder()
                    .conversationId(conversation.getId())
                    .userId(friendId)
                    .build());
        } else {
            assertConversationMember(userId, conversation.getId());
        }

        Map<Long, String> remarkMap = loadRemarkMap(userId, Set.of(friendId));
        return toConversationVO(conversation, friend, remarkMap.get(friendId));
    }

    @Override
    public List<MessageVO> listMessages(Long userId, Long conversationId, Long beforeMessageId, int limit) {
        assertConversationMember(userId, conversationId);
        int pageSize = limit <= 0 ? DEFAULT_MESSAGE_LIMIT : Math.min(limit, MAX_MESSAGE_LIMIT);

        QueryWrapper query = QueryWrapper.create()
                .where(ImMessage::getConversationId).eq(conversationId)
                .orderBy(ImMessage::getCreateTime, false)
                .limit(pageSize);

        if (beforeMessageId != null) {
            ImMessage before = messageMapper.selectOneById(beforeMessageId);
            if (before != null && before.getCreateTime() != null) {
                query.and(ImMessage::getCreateTime).lt(before.getCreateTime());
            }
        }

        List<ImMessage> messages = messageMapper.selectListByQuery(query);
        if (messages.isEmpty()) {
            return List.of();
        }

        messages.sort(Comparator.comparing(ImMessage::getCreateTime));
        Set<Long> senderIds = messages.stream().map(ImMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(senderIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<MessageVO> result = new ArrayList<>();
        for (ImMessage message : messages) {
            SysUser sender = senderMap.get(message.getSenderId());
            result.add(toMessageVO(message, sender, userId));
        }
        return result;
    }

    @Override
    @Transactional
    public MessageVO sendMessage(Long userId, SendMessageDTO dto) {
        assertConversationMember(userId, dto.getConversationId());

        // 幂等去重：同一 senderId + clientMsgId 在 10 分钟内只入库一次
        // 防止网络抖动导致客户端重发产生重复消息
        if (StringUtils.hasText(dto.getClientMsgId())) {
            String dedupKey = buildClientMsgDedupKey(userId, dto.getClientMsgId());
            Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(
                    dedupKey, "1", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(firstTime)) {
                // 已存在该 clientMsgId 对应的记录，返回最近一条（按 createTime 倒序）
                ImMessage existing = messageMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImMessage::getSenderId).eq(userId)
                                .orderBy(ImMessage::getCreateTime, false)
                                .limit(1));
                if (existing != null) {
                    SysUser sender = sysUserMapper.selectOneById(userId);
                    return toMessageVO(existing, sender, userId);
                }
            }
        }

        ImConversation conversation = conversationMapper.selectOneById(dto.getConversationId());
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        if (conversation.getType() == ImConversation.TYPE_PRIVATE) {
            Long peerId = resolvePrivatePeerId(userId, conversation.getId());
            assertFriendship(userId, peerId);
        }

        String msgType = normalizeMsgType(dto.getMsgType());
        validateMessagePayload(msgType, dto);

        ImMessage message = ImMessage.builder()
                .conversationId(dto.getConversationId())
                .senderId(userId)
                .type(msgType)
                .content(resolveContent(msgType, dto))
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .fileUrl(dto.getFileUrl())
                .voiceDuration(dto.getVoiceDuration())
                .build();
        messageMapper.insert(message);

        Date now = message.getCreateTime() != null ? message.getCreateTime() : new Date();
        conversation.setLastMessageContent(buildPreview(message));
        conversation.setLastMessageTime(now);
        conversationMapper.update(conversation);

        SysUser sender = sysUserMapper.selectOneById(userId);
        return toMessageVO(message, sender, userId);
    }

    private String buildClientMsgDedupKey(Long userId, String clientMsgId) {
        return "linkx:msg:dedup:" + userId + ":" + clientMsgId;
    }

    @Override
    public ChatFileUploadVO uploadChatFile(Long userId, Long conversationId, MultipartFile file) {
        assertConversationMember(userId, conversationId);
        try {
            // 出于安全，使用 UUID 文件名而非 conversationId-based 路径
            String objectKey = fileStorageService.uploadFile(file, null);
            // 立即生成签名 URL 返回给前端，避免将对象 key 暴露到数据库
            String signedUrl = fileStorageService.getPresignedUrl(objectKey);
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

    private void assertFriendship(Long userId, Long friendId) {
        SysUserRelation relation = sysUserRelationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
        );
        if (relation == null) {
            throw new CustomException(403, "只能与好友聊天");
        }
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
                // 跳过无法解析的单聊会话，避免影响整个列表加载
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

    private ConversationVO toConversationVO(ImConversation conversation, SysUser peer, String remark) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .peerUserId(peer.getId())
                .peerUsername(peer.getUsername())
                .peerNickname(peer.getNickname())
                .peerAvatar(mediaUrlService.resolve(peer.getAvatar()))
                .peerRemark(remark)
                .lastMessage(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime() != null
                        ? conversation.getLastMessageTime().getTime()
                        : null)
                .build();
    }

    private ConversationVO toGroupConversationVO(ImConversation conversation) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .announcement(conversation.getAnnouncement())
                .ownerId(conversation.getOwnerId())
                .lastMessage(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime() != null
                        ? conversation.getLastMessageTime().getTime()
                        : null)
                .build();
    }

    private MessageVO toMessageVO(ImMessage message, SysUser sender, Long currentUserId) {
        MessageVO.MessageVOBuilder builder = MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderNickname(sender != null ? sender.getNickname() : null)
                .senderAvatar(sender != null ? sender.getAvatar() : null)
                .type(message.getType())
                .content(message.getContent())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileUrl(message.getFileUrl())
                .voiceDuration(message.getVoiceDuration())
                .createTime(message.getCreateTime() != null ? message.getCreateTime().getTime() : null)
                .isSelf(message.getSenderId().equals(currentUserId));

        // 红包消息：填充语义化字段，前端可直接渲染（无需自行换算 fileSize/fileUrl/fileName）
        if (ImMessage.TYPE_RED_PACKET.equals(message.getType()) && message.getFileUrl() != null) {
            fillRedPacketFields(builder, message, currentUserId);
        }

        return builder.build();
    }

    /**
     * 读取红包当前状态并填充到 MessageVO（含当前用户视角的领取情况）。
     */
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
    }

    private String resolveContent(String msgType, SendMessageDTO dto) {
        if (ImMessage.TYPE_TEXT.equals(msgType)) {
            // HTML 转义防 XSS
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
            default -> message.getContent();
        };
    }
}
