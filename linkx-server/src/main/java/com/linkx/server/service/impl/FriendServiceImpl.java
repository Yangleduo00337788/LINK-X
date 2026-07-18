package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserSearchVO;
import com.linkx.server.entity.SysFriendRequest;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysFriendRequestMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.MediaUrlService;
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
    private final MediaUrlService mediaUrlService;

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
        SysFriendRequest request = SysFriendRequest.builder()
                .fromUserId(fromUserId)
                .toUserId(target.getId())
                .message(StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : null)
                .status(SysFriendRequest.STATUS_PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        sysFriendRequestMapper.insert(request);
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

        List<FriendItemVO> result = new ArrayList<>();
        for (SysUserRelation relation : relations) {
            SysUser friend = friendMap.get(relation.getFriendId());
            if (friend == null) {
                continue;
            }
            result.add(FriendItemVO.builder()
                    .userId(friend.getId())
                    .username(friend.getUsername())
                    .nickname(friend.getNickname())
                    .avatar(mediaUrlService.resolve(friend.getAvatar()))
                    .remark(remarkMap.get(friend.getId()))
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
        Date now = new Date();
        if (!isFriend(userA, userB)) {
            sysUserRelationMapper.insert(SysUserRelation.builder()
                    .userId(userA)
                    .friendId(userB)
                    .status(RELATION_STATUS_NORMAL)
                    .createTime(now)
                    .updateTime(now)
                    .build());
        }
        if (!isFriend(userB, userA)) {
            sysUserRelationMapper.insert(SysUserRelation.builder()
                    .userId(userB)
                    .friendId(userA)
                    .status(RELATION_STATUS_NORMAL)
                    .createTime(now)
                    .updateTime(now)
                    .build());
        }
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
                    .fromAvatar(fromUser != null ? fromUser.getAvatar() : null)
                    .peerUserId(peerUser != null ? peerUser.getId() : null)
                    .peerUsername(peerUser != null ? peerUser.getUsername() : "")
                    .peerNickname(peerUser != null ? peerUser.getNickname() : "")
                    .peerAvatar(peerUser != null ? peerUser.getAvatar() : null)
                    .message(request.getMessage())
                    .status(request.getStatus())
                    .direction(direction)
                    .createTime(request.getCreateTime())
                    .build();
        }).toList();
    }
}
