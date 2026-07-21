package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserSearchVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysFriendRequest;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImChannelManager;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysFriendRequestMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.UserPreferenceService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private static final int SEARCH_LIMIT = 20;
    private static final int RELATION_STATUS_NORMAL = 1;

    private final SysUserMapper sysUserMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final SysFriendRequestMapper sysFriendRequestMapper;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final MediaUrlService mediaUrlService;
    private final UserPreferenceService userPreferenceService;
    private final ImChannelManager imChannelManager;
    private final ImMessagePushService imPushService;

    @Override
    public List<UserSearchVO> searchUsers(String keyword, Long currentUserId) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() < 2) {
            throw new CustomException(400, "搜索关键词至少2个字符");
        }

        // 优先精确匹配 LinkX ID（username）
        SysUser exactUser = sysUserMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysUser::getUsername).eq(q)
                        .and(SysUser::getId).ne(currentUserId)
                        .and(SysUser::getStatus).eq(1)
        );
        if (exactUser != null) {
            return List.of(toSearchVO(exactUser));
        }

        List<SysUser> byUsername = sysUserMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUser::getUsername).like(q)
                        .and(SysUser::getId).ne(currentUserId)
                        .and(SysUser::getStatus).eq(1)
        );
        List<SysUser> byNickname = sysUserMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUser::getNickname).like(q)
                        .and(SysUser::getId).ne(currentUserId)
                        .and(SysUser::getStatus).eq(1)
        );

        Map<Long, SysUser> merged = new java.util.LinkedHashMap<>();
        for (SysUser user : byUsername) {
            merged.putIfAbsent(user.getId(), user);
        }
        for (SysUser user : byNickname) {
            merged.putIfAbsent(user.getId(), user);
        }

        List<SysUser> users = merged.values().stream().limit(SEARCH_LIMIT).toList();

        return users.stream().map(this::toSearchVO).toList();
    }

    private UserSearchVO toSearchVO(SysUser user) {
        return UserSearchVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(mediaUrlService.resolve(user.getAvatar()))
                .build();
    }

    @Override
    @Transactional
    public void sendFriendRequest(Long fromUserId, SendFriendRequestDTO dto) {
        SysUser target = sysUserMapper.selectOneByQuery(
                QueryWrapper.create().where(SysUser::getUsername).eq(dto.getUsername().trim())
        );
        if (target == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (target.getId().equals(fromUserId)) {
            throw new CustomException(400, "不能添加自己为好友");
        }
        if (isFriend(fromUserId, target.getId())) {
            throw new CustomException(400, "对方已是你的好友");
        }

        SysFriendRequest reversePending = sysFriendRequestMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysFriendRequest::getFromUserId).eq(target.getId())
                        .and(SysFriendRequest::getToUserId).eq(fromUserId)
                        .and(SysFriendRequest::getStatus).eq(SysFriendRequest.STATUS_PENDING)
        );
        if (reversePending != null) {
            acceptFriendRequest(fromUserId, reversePending.getId());
            return;
        }

        SysFriendRequest existing = sysFriendRequestMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysFriendRequest::getFromUserId).eq(fromUserId)
                        .and(SysFriendRequest::getToUserId).eq(target.getId())
                        .and(SysFriendRequest::getStatus).eq(SysFriendRequest.STATUS_PENDING)
        );
        if (existing != null) {
            throw new CustomException(400, "已发送好友申请，请等待对方处理");
        }

        Date now = new Date();
        // 对方关闭「加好友需验证」：直接成为好友
        if (!userPreferenceService.requiresFriendVerify(target.getId())) {
            SysFriendRequest autoAccepted = SysFriendRequest.builder()
                    .fromUserId(fromUserId)
                    .toUserId(target.getId())
                    .message(StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : null)
                    .status(SysFriendRequest.STATUS_ACCEPTED)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            sysFriendRequestMapper.insert(autoAccepted);
            createBidirectionalRelation(fromUserId, target.getId());
            imPushService.pushToUser(target.getId(), "notification_refresh", Map.of("type", "friend_accepted"));
            imPushService.pushToUser(fromUserId, "notification_refresh", Map.of("type", "friend_accepted"));
            return;
        }

        SysFriendRequest request = SysFriendRequest.builder()
                .fromUserId(fromUserId)
                .toUserId(target.getId())
                .message(StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : null)
                .status(SysFriendRequest.STATUS_PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        sysFriendRequestMapper.insert(request);
        // 实时通知被申请人：刷新好友通知角标
        imPushService.pushToUser(target.getId(), "notification_refresh", Map.of("type", "friend_request"));
    }

    @Override
    public List<FriendRequestVO> listIncomingRequests(Long userId) {
        List<SysFriendRequest> requests = sysFriendRequestMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysFriendRequest::getToUserId).eq(userId)
                        .orderBy(SysFriendRequest::getCreateTime, false)
        );
        return toRequestVOs(requests, "incoming");
    }

    @Override
    public List<FriendRequestVO> listOutgoingRequests(Long userId) {
        List<SysFriendRequest> requests = sysFriendRequestMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysFriendRequest::getFromUserId).eq(userId)
                        .orderBy(SysFriendRequest::getCreateTime, false)
        );
        return toRequestVOs(requests, "outgoing");
    }

    @Override
    @Transactional
    public void acceptFriendRequest(Long userId, Long requestId) {
        SysFriendRequest request = requireRequest(requestId);
        if (!request.getToUserId().equals(userId)) {
            throw new CustomException(403, "无权处理该好友申请");
        }
        if (request.getStatus() != SysFriendRequest.STATUS_PENDING) {
            throw new CustomException(400, "该申请已处理");
        }

        request.setStatus(SysFriendRequest.STATUS_ACCEPTED);
        request.setUpdateTime(new Date());
        sysFriendRequestMapper.update(request);

        createBidirectionalRelation(request.getFromUserId(), request.getToUserId());
        imPushService.pushToUser(request.getFromUserId(), "notification_refresh", Map.of("type", "friend_accepted"));
    }

    @Override
    @Transactional
    public void rejectFriendRequest(Long userId, Long requestId) {
        SysFriendRequest request = requireRequest(requestId);
        if (!request.getToUserId().equals(userId)) {
            throw new CustomException(403, "无权处理该好友申请");
        }
        if (request.getStatus() != SysFriendRequest.STATUS_PENDING) {
            throw new CustomException(400, "该申请已处理");
        }

        request.setStatus(SysFriendRequest.STATUS_REJECTED);
        request.setUpdateTime(new Date());
        sysFriendRequestMapper.update(request);
        imPushService.pushToUser(request.getFromUserId(), "notification_refresh", Map.of("type", "friend_rejected"));
    }

    @Override
    public List<FriendItemVO> listFriends(Long userId) {
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
                        .orderBy(SysUserRelation::getCreateTime, false)
        );
        if (relations.isEmpty()) {
            return List.of();
        }

        Map<Long, String> remarkMap = new java.util.HashMap<>();
        for (SysUserRelation relation : relations) {
            remarkMap.put(relation.getFriendId(), relation.getRemark());
        }

        List<Long> friendIds = relations.stream().map(SysUserRelation::getFriendId).toList();
        List<SysUser> friends = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(friendIds)
        );
        Map<Long, SysUser> friendMap = friends.stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Boolean> showOnlineMap = userPreferenceService.batchShowsOnlineStatus(friendIds);

        List<FriendItemVO> result = new ArrayList<>();
        for (SysUserRelation relation : relations) {
            SysUser friend = friendMap.get(relation.getFriendId());
            if (friend == null) {
                continue;
            }
            boolean showOnline = !Boolean.FALSE.equals(showOnlineMap.get(friend.getId()));
            boolean online = showOnline && imChannelManager.isOnline(friend.getId());
            result.add(FriendItemVO.builder()
                    .userId(friend.getId())
                    .username(friend.getUsername())
                    .nickname(friend.getNickname())
                    .avatar(mediaUrlService.resolve(friend.getAvatar()))
                    .remark(remarkMap.get(friend.getId()))
                    .online(online)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        if (!isFriend(userId, friendId)) {
            throw new CustomException(404, "对方不是你的好友");
        }
        removeRelation(userId, friendId);
        removeRelation(friendId, userId);
        // 双向退出单聊会话，避免删除后仍出现在消息列表并可继续发消息
        dissolvePrivateConversationMembership(userId, friendId);
        Map<String, Object> payload = Map.of(
                "type", "friend_deleted",
                "peerUserId", String.valueOf(friendId)
        );
        Map<String, Object> peerPayload = Map.of(
                "type", "friend_deleted",
                "peerUserId", String.valueOf(userId)
        );
        imPushService.pushToUser(userId, "notification_refresh", payload);
        imPushService.pushToUser(friendId, "notification_refresh", peerPayload);
    }

    /** 逻辑删除双方在私聊会话中的成员关系 */
    private void dissolvePrivateConversationMembership(Long userA, Long userB) {
        String privateKey = userA < userB ? userA + "_" + userB : userB + "_" + userA;
        ImConversation conversation = conversationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getType).eq(ImConversation.TYPE_PRIVATE)
                        .and(ImConversation::getPrivateKey).eq(privateKey)
        );
        if (conversation == null) {
            return;
        }
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversation.getId())
        );
        for (ImConversationMember member : members) {
            memberMapper.deleteById(member.getId());
        }
    }

    private SysFriendRequest requireRequest(Long requestId) {
        SysFriendRequest request = sysFriendRequestMapper.selectOneById(requestId);
        if (request == null) {
            throw new CustomException(404, "好友申请不存在");
        }
        return request;
    }

    private boolean isFriend(Long userId, Long friendId) {
        return sysUserRelationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
                        .and(SysUserRelation::getStatus).eq(RELATION_STATUS_NORMAL)
        ) > 0;
    }

    private void createBidirectionalRelation(Long userA, Long userB) {
        ensureRelation(userA, userB);
        ensureRelation(userB, userA);
    }

    /**
     * 确保 A→B 好友关系存在。
     * 删除好友是逻辑删除，唯一键 uk_user_friend 仍占用，因此需恢复旧行而非再 insert。
     */
    private void ensureRelation(Long userId, Long friendId) {
        if (isFriend(userId, friendId)) {
            return;
        }
        Date now = new Date();
        SysUserRelation existing = LogicDeleteManager.execWithoutLogicDelete(() ->
                sysUserRelationMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(SysUserRelation::getUserId).eq(userId)
                                .and(SysUserRelation::getFriendId).eq(friendId)
                                .limit(1)
                )
        );
        if (existing != null) {
            existing.setStatus(RELATION_STATUS_NORMAL);
            existing.setDeleted(0);
            existing.setUpdateTime(now);
            // update 默认也会带逻辑删除条件；对已删除行需绕过
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                sysUserRelationMapper.update(existing);
                return null;
            });
            return;
        }
        sysUserRelationMapper.insert(SysUserRelation.builder()
                .userId(userId)
                .friendId(friendId)
                .status(RELATION_STATUS_NORMAL)
                .deleted(0)
                .createTime(now)
                .updateTime(now)
                .build());
    }

    private void removeRelation(Long userId, Long friendId) {
        SysUserRelation relation = sysUserRelationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysUserRelation::getUserId).eq(userId)
                        .and(SysUserRelation::getFriendId).eq(friendId)
        );
        if (relation != null) {
            sysUserRelationMapper.deleteById(relation.getId());
        }
    }

    private List<FriendRequestVO> toRequestVOs(List<SysFriendRequest> requests, String direction) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = requests.stream()
                .flatMap(request -> java.util.stream.Stream.of(request.getFromUserId(), request.getToUserId()))
                .distinct()
                .toList();
        List<SysUser> users = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds)
        );
        Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return requests.stream().map(request -> {
            SysUser fromUser = userMap.get(request.getFromUserId());
            SysUser toUser = userMap.get(request.getToUserId());
            SysUser peerUser = "incoming".equals(direction) ? fromUser : toUser;
            return FriendRequestVO.builder()
                    .id(request.getId())
                    .fromUserId(request.getFromUserId())
                    .toUserId(request.getToUserId())
                    .fromUsername(fromUser != null ? fromUser.getUsername() : "")
                    .fromNickname(fromUser != null ? fromUser.getNickname() : "")
                    .fromAvatar(fromUser != null ? mediaUrlService.resolve(fromUser.getAvatar()) : null)
                    .peerUserId(peerUser != null ? peerUser.getId() : null)
                    .peerUsername(peerUser != null ? peerUser.getUsername() : "")
                    .peerNickname(peerUser != null ? peerUser.getNickname() : "")
                    .peerAvatar(peerUser != null ? mediaUrlService.resolve(peerUser.getAvatar()) : null)
                    .message(request.getMessage())
                    .status(request.getStatus())
                    .direction(direction)
                    .createTime(request.getCreateTime())
                    .build();
        }).toList();
    }
}
