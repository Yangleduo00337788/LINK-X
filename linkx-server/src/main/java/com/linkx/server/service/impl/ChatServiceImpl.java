package com.linkx.server.service.impl;

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
import com.linkx.server.service.UserPreferenceService;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int RELATION_STATUS_NORMAL = 1;
    /** 撤回时限（毫秒），与微信一致约 2 分钟 */
    private static final long RECALL_WINDOW_MS = 2 * 60 * 1000L;

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
    private final UserPreferenceService userPreferenceService;
    private final ImChannelManager imChannelManager;

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
            if (conversation.getType() == ImConversation.TYPE_PRIVATE) {
                SysUser peer = peerUserMap.get(conversation.getId());
                if (peer == null) {
                    continue;
                }
                // 已非好友的单聊不进入会话列表（删除好友后即时隐藏）
                if (!isFriend(userId, peer.getId())) {
                    continue;
                }
                boolean showOnline = !Boolean.FALSE.equals(showOnlineMap.get(peer.getId()));
                boolean peerOnline = showOnline && imChannelManager.isOnline(peer.getId());
                result.add(toConversationVO(conversation, peer, remarkMap.get(peer.getId()), peerOnline));
            } else if (conversation.getType() == ImConversation.TYPE_GROUP) {
                result.add(toGroupConversationVO(
                        conversation,
                        groupMemberAvatars.getOrDefault(conversation.getId(), List.of()),
                        groupRemarkMap.get(conversation.getId())
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
            // 删除好友会逻辑删除成员；重新成为好友后需恢复，否则无法进入会话
            ensurePrivateMembership(conversation.getId(), userId);
            ensurePrivateMembership(conversation.getId(), friendId);
        }

        Map<Long, String> remarkMap = loadRemarkMap(userId, Set.of(friendId));
        return toConversationVO(conversation, friend, remarkMap.get(friendId), resolvePeerOnline(friendId));
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
            // 删除好友后禁止继续私聊（陌生人会话需重新发起才会建成员关系）
            if (!isFriend(userId, peerId)) {
                throw new CustomException(403, "对方已不是好友，无法发送消息");
            }
        } else if (conversation.getType() == ImConversation.TYPE_GROUP) {
            assertGroupSpeakAllowed(userId, conversation);
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
            return toMessageVO(message, sender, userId);
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
        return toMessageVO(message, sender, userId);
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
                .createTime(now)
                .build();
        messageMapper.insert(message);
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }

        conversation.setLastMessageContent(text);
        conversation.setLastMessageTime(message.getCreateTime());
        conversationMapper.update(conversation);

        SysUser sender = operatorId != null ? sysUserMapper.selectOneById(operatorId) : null;
        return toMessageVO(message, sender, operatorId);
    }

    /**
     * 按会话最新一条消息刷新会话列表预览（撤回后可能仍是该条，也可能需回退到更早消息）。
     */
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
    public List<ChatSearchHitVO> searchMessages(Long userId, String keyword, String type, Long conversationId, int limit) {
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

        List<ImMessage> messages = messageMapper.selectListByQuery(qw);
        if (messages.isEmpty()) {
            // 同时按文件名搜
            QueryWrapper fileQw = QueryWrapper.create()
                    .where(ImMessage::getConversationId).in(allowedIds)
                    .and(ImMessage::getType).ne(ImMessage.TYPE_RECALL)
                    .and(ImMessage::getFileName).like(q)
                    .orderBy(ImMessage::getCreateTime, false)
                    .limit(cap);
            if (StringUtils.hasText(type)) {
                fileQw.and(ImMessage::getType).eq(type.trim());
            }
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
                            : mediaUrlService.resolve(msg.getFileUrl()))
                    .createTime(msg.getCreateTime() == null ? null : msg.getCreateTime().getTime())
                    .build());
        }
        return hits;
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

    /**
     * 群聊发言校验：全体禁言下仅群主/管理员可发言；被个人禁言者不可发言。
     */
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
     * 确保用户是会话成员；若曾逻辑删除则恢复，避免唯一键冲突。
     */
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

    private boolean isFriend(Long userId, Long friendId) {
        return sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
        ) > 0;
    }

    /**
     * 私聊权限：好友可聊；非好友时，对方或自己开启「允许陌生人会话」也可聊（便于陌生人发起与回复）。
     */
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

    /** 结合 IM 在线与对方「在线状态可见」偏好 */
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

    private ConversationVO toConversationVO(
            ImConversation conversation,
            SysUser peer,
            String remark,
            boolean peerOnline
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
                .build();
    }

    private ConversationVO toGroupConversationVO(
            ImConversation conversation,
            List<GroupMemberAvatarVO> memberAvatars,
            String myRemark
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
                .build();
    }

    /** 批量加载当前用户对各群的备注 */
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

    /**
     * 批量加载群成员头像预览（每群最多 9 人，群主优先），供前端拼图头像。
     */
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

    private MessageVO toMessageVO(ImMessage message, SysUser sender, Long currentUserId) {
        String fileUrl = message.getFileUrl();
        // 红包消息的 fileUrl 存的是红包 ID，不能当媒体 key 签发
        if (fileUrl != null && !ImMessage.TYPE_RED_PACKET.equals(message.getType())) {
            fileUrl = mediaUrlService.resolve(fileUrl);
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
            case ImMessage.TYPE_RECALL -> "撤回了一条消息";
            case ImMessage.TYPE_SYSTEM -> message.getContent() != null ? message.getContent() : "[系统消息]";
            default -> message.getContent();
        };
    }
}
