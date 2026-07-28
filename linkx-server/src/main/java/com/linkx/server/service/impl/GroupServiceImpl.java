package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.MuteAllDTO;
import com.linkx.server.controller.dto.MuteMemberDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupJoinRequestVO;
import com.linkx.server.controller.vo.GroupMemberAvatarVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.GroupAnnouncement;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.GroupInvitation;
import com.linkx.server.entity.MessageNotification;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.GroupAnnouncementMapper;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.SysUserMapper;

import java.util.Objects;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群聊服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final MediaUrlService mediaUrlService;
    private final ChatService chatService;
    private final ImMessagePushService imPushService;
    private final MessageNotificationService notificationService;
    private final MessageNotificationMapper notificationMapper;
    private final GroupAnnouncementMapper groupAnnouncementMapper;
    private final GroupAssetMapper groupAssetMapper;
    private final GroupInvitationMapper groupInvitationMapper;
    private final com.linkx.server.mapper.ImMessageMapper messageMapper;
    private final com.linkx.server.service.FileStorageService fileStorageService;

    private static final String NOTIFY_TYPE_GROUP_JOIN_REQUEST = "group_join_request";
    /** 群成员上限（默认 500，与审查建议一致） */
    private static final int MAX_GROUP_MEMBERS = 500;

    @Override
    @Transactional
    public GroupConversationVO createGroup(Long userId, CreateGroupDTO dto) {
        // 获取当前用户信息
        SysUser creator = sysUserMapper.selectOneById(userId);
        if (creator == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 验证成员是否都是好友
        List<SysUser> members = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(dto.getMemberIds())
        );
        if (members.size() != dto.getMemberIds().size()) {
            throw new CustomException(400, "部分成员不存在");
        }
        // 创建者 + 初始成员不得超过上限
        if (dto.getMemberIds().size() + 1 > MAX_GROUP_MEMBERS) {
            throw new CustomException(400, "群成员不得超过 " + MAX_GROUP_MEMBERS + " 人");
        }

        // 创建群会话
        ImConversation group = ImConversation.builder()
                .type(ImConversation.TYPE_GROUP)
                .name(dto.getName())
                .ownerId(userId)
                .muteAll(0)
                .deleted(0)
                .build();
        conversationMapper.insert(group);

        // 添加创建者为群主
        memberMapper.insert(ImConversationMember.builder()
                .conversationId(group.getId())
                .userId(userId)
                .role(ImConversationMember.ROLE_OWNER)
                .muted(0)
                .deleted(0)
                .build());

        // 添加其他成员
        for (Long memberId : dto.getMemberIds()) {
            memberMapper.insert(ImConversationMember.builder()
                    .conversationId(group.getId())
                    .userId(memberId)
                    .role(ImConversationMember.ROLE_MEMBER)
                    .muted(0)
                    .deleted(0)
                    .build());
        }

        String creatorName = displayName(creator);
        emitSystemTip(userId, group.getId(), creatorName + " 发起了群聊");

        return toGroupConversationVO(group, creator, userId);
    }

    @Override
    public List<ConversationVO> listGroups(Long userId) {
        // 获取用户所在的群会话
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<Long> conversationIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());

        List<ImConversation> groups = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getType).eq(ImConversation.TYPE_GROUP)
                        .and(ImConversation::getId).in(conversationIds)
        );

        // 按最后消息时间排序
        groups.sort(Comparator.comparing(
                ImConversation::getLastMessageTime,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        Map<Long, List<GroupMemberAvatarVO>> avatarMap = loadGroupMemberAvatarPreviews(
                groups.stream().map(ImConversation::getId).collect(Collectors.toSet())
        );

        List<ConversationVO> result = new ArrayList<>();
        for (ImConversation group : groups) {
            result.add(toConversationVO(group, avatarMap.getOrDefault(group.getId(), List.of())));
        }
        return result;
    }

    @Override
    public GroupConversationVO getGroupInfo(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }

        SysUser owner = sysUserMapper.selectOneById(group.getOwnerId());
        return toGroupConversationVO(group, owner, userId);
    }

    @Override
    @Transactional
    public GroupConversationVO updateGroup(Long userId, Long conversationId, UpdateGroupDTO dto) {
        boolean rename = StringUtils.hasText(dto.getName());
        boolean updateAnnouncement = dto.getAnnouncement() != null;
        // 改群名仅群主；发/改公告群主与管理员均可
        ImConversation group = rename
                ? assertGroupOwner(userId, conversationId)
                : assertGroupAdmin(userId, conversationId);
        if (!rename && !updateAnnouncement) {
            // 空更新也走管理员校验后直接返回
            SysUser owner = sysUserMapper.selectOneById(group.getOwnerId());
            return toGroupConversationVO(group, owner, userId);
        }

        String oldName = group.getName();
        if (rename) {
            group.setName(dto.getName());
        }
        if (updateAnnouncement) {
            group.setAnnouncement(dto.getAnnouncement());
        }
        conversationMapper.update(group);

        SysUser owner = sysUserMapper.selectOneById(group.getOwnerId());
        SysUser operator = sysUserMapper.selectOneById(userId);
        // 构建更新后的群信息
        GroupConversationVO updatedVO = toGroupConversationVO(group, owner, null);

        // 推送群信息变更给所有群成员
        String action = rename ? "group_renamed" : "group_announcement_updated";
        String tipContent = rename
                ? displayName(operator) + " 修改群名为「" + dto.getName() + "」"
                : displayName(operator) + " 更新了群公告";
        final GroupConversationVO finalVO = updatedVO;
        final String finalAction = action;
        emitGroupUpdate(group.getId(), finalAction, finalVO);
        if (rename) {
            emitSystemTip(userId, group.getId(), tipContent);
        }

        return updatedVO;
    }

    @Override
    public List<GroupMemberVO> listMembers(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);

        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getDeleted).eq(0)
        );

        Set<Long> userIds = members.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());

        Map<Long, SysUser> userMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<GroupMemberVO> result = new ArrayList<>();
        Date now = new Date();
        for (ImConversationMember member : members) {
            SysUser user = userMap.get(member.getUserId());
            if (user != null) {
                result.add(GroupMemberVO.builder()
                        .userId(user.getId())
                        .nickname(user.getNickname())
                        .avatar(mediaUrlService.resolve(user.getAvatar()))
                        .role(member.getRole())
                        .joinTime(member.getCreateTime() != null ? member.getCreateTime().getTime() : null)
                        .muted(isMemberMuteActive(member, now))
                        .muteUntil(member.getMuteUntil() != null ? member.getMuteUntil().getTime() : null)
                        .build());
            }
        }
        return result;
    }

    @Override
    @Transactional
    public List<GroupMemberVO> addMembers(Long userId, Long conversationId, AddGroupMembersDTO dto) {
        assertGroupAdmin(userId, conversationId);

        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }

        // 群邀请策略：ownerApprove 时仅群主/管理员可拉人；addMembers 同样受约束
        enforceInvitePolicy(userId, group);

        // 预检容量（保守：按请求人数估算，已在群中的会被 ensureActiveMembership 跳过）
        long activeCount = memberMapper.selectCountByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        long requested = dto.getMemberIds().stream().filter(Objects::nonNull).distinct().count();
        if (activeCount + requested > MAX_GROUP_MEMBERS) {
            throw new CustomException(400, "群成员已达上限（" + MAX_GROUP_MEMBERS + " 人）");
        }

        List<ImConversationMember> addedMembers = new ArrayList<>();
        for (Long memberId : dto.getMemberIds()) {
            if (memberId == null) {
                continue;
            }
            ImConversationMember ensured = ensureActiveMembership(conversationId, memberId);
            if (ensured != null) {
                addedMembers.add(ensured);
            }
        }

        // 直接拉人后，清理这些人的待确认邀请，避免对方通知里仍显示可接受/拒绝
        Set<Long> addedUserIds = addedMembers.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());
        if (!addedUserIds.isEmpty()) {
            markPendingInvitationsAccepted(conversationId, addedUserIds);
            SysUser operator = sysUserMapper.selectOneById(userId);
            // 构建群会话信息，用于前端直接加入会话列表
            GroupConversationVO groupVO = toGroupConversationVO(group, operator, null);
            for (Long addedId : addedUserIds) {
                SysUser u = sysUserMapper.selectOneById(addedId);
                emitSystemTip(userId, conversationId, displayName(operator) + " 邀请 " + displayName(u) + " 加入了群聊");
                // 推送通知刷新 + 群会话信息（用于前端直接加入会话列表）
                imPushService.pushToUser(addedId, "notification_refresh", Map.of(
                        "type", "group_invitation",
                        "conversationId", String.valueOf(conversationId)
                ));
                // 推送 group_added 事件，携带群会话信息，前端收到后直接加入会话列表
                imPushService.pushToUser(addedId, "group_added", Map.of(
                        "conversationId", String.valueOf(conversationId),
                        "group", groupVO
                ));
            }
        }

        if (addedMembers.isEmpty()) {
            return List.of();
        }

        Set<Long> newUserIds = addedMembers.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(newUserIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<GroupMemberVO> result = new ArrayList<>();
        for (ImConversationMember member : addedMembers) {
            SysUser user = userMap.get(member.getUserId());
            if (user != null) {
                result.add(GroupMemberVO.builder()
                        .userId(user.getId())
                        .nickname(user.getNickname())
                        .avatar(mediaUrlService.resolve(user.getAvatar()))
                        .role(member.getRole())
                        .joinTime(member.getCreateTime() != null ? member.getCreateTime().getTime() : null)
                        .build());
            }
        }
        return result;
    }

    /**
     * 确保用户是活跃群成员。
     * 退群是逻辑删除且 uk_conv_user 仍占用，需恢复旧行而非再 insert。
     *
     * @return 新加入或恢复的成员行；若本来就是活跃成员则返回 null
     */
    private ImConversationMember ensureActiveMembership(Long conversationId, Long memberId) {
        ImConversationMember active = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(memberId)
        );
        if (active != null) {
            return null;
        }
        // 新成员或恢复软删成员前校验上限
        assertGroupNotFull(conversationId);
        ImConversationMember softDeleted = LogicDeleteManager.execWithoutLogicDelete(() ->
                memberMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImConversationMember::getConversationId).eq(conversationId)
                                .and(ImConversationMember::getUserId).eq(memberId)
                                .limit(1)
                )
        );
        if (softDeleted != null) {
            softDeleted.setDeleted(0);
            softDeleted.setRole(ImConversationMember.ROLE_MEMBER);
            softDeleted.setMuted(0);
            softDeleted.setMuteUntil(null);
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                memberMapper.update(softDeleted);
                return null;
            });
            return softDeleted;
        }
        ImConversationMember newMember = ImConversationMember.builder()
                .conversationId(conversationId)
                .userId(memberId)
                .role(ImConversationMember.ROLE_MEMBER)
                .muted(0)
                .deleted(0)
                .build();
        memberMapper.insert(newMember);
        return newMember;
    }

    private void assertGroupNotFull(Long conversationId) {
        long count = memberMapper.selectCountByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        if (count >= MAX_GROUP_MEMBERS) {
            throw new CustomException(400, "群成员已达上限（" + MAX_GROUP_MEMBERS + " 人）");
        }
    }

    private void markPendingInvitationsAccepted(Long conversationId, Set<Long> inviteeUserIds) {
        if (inviteeUserIds == null || inviteeUserIds.isEmpty()) {
            return;
        }
        List<GroupInvitation> pending = groupInvitationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupInvitation::getConversationId).eq(conversationId)
                        .and(GroupInvitation::getInviteeUserId).in(inviteeUserIds)
                        .and(GroupInvitation::getStatus).eq(GroupInvitation.STATUS_PENDING)
        );
        for (GroupInvitation inv : pending) {
            // uk(conversation, invitee, status)：清掉历史 accepted，避免 pending→accepted 冲突
            List<GroupInvitation> oldAccepted = groupInvitationMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(GroupInvitation::getConversationId).eq(conversationId)
                            .and(GroupInvitation::getInviteeUserId).eq(inv.getInviteeUserId())
                            .and(GroupInvitation::getStatus).eq(GroupInvitation.STATUS_ACCEPTED)
            );
            for (GroupInvitation old : oldAccepted) {
                groupInvitationMapper.deleteById(old.getId());
            }
            inv.setStatus(GroupInvitation.STATUS_ACCEPTED);
            groupInvitationMapper.update(inv);
        }
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long conversationId, Long memberId) {
        ImConversation group = assertGroupAdmin(userId, conversationId);

        // 不能移除群主
        if (Objects.equals(group.getOwnerId(), memberId)) {
            throw new CustomException(400, "不能移除群主");
        }

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(memberId)
        );
        if (member == null) {
            throw new CustomException(404, "该成员不在群中");
        }

        // 非群主不能移除管理员：与批量接口行为保持一致
        boolean operatorIsOwner = Objects.equals(group.getOwnerId(), userId);
        if (!operatorIsOwner && ImConversationMember.ROLE_ADMIN.equals(member.getRole())) {
            throw new CustomException(403, "管理员不能移除其他管理员");
        }

        memberMapper.deleteById(member.getId());
    }

    @Override
    @Transactional
    public void quitGroup(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }

        // 群主不能退群，只能解散
        if (group.getOwnerId().equals(userId)) {
            throw new CustomException(400, "群主不能退出群聊，请先转让群主或解散群聊");
        }

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(404, "你不在该群中");
        }

        memberMapper.deleteById(member.getId());
    }

    @Override
    @Transactional
    public void dissolveGroup(Long userId, Long conversationId) {
        ImConversation group = assertGroupOwner(userId, conversationId);

        // 先收集群资产 MinIO key，DB 清理后删对象
        List<GroupAsset> assets = groupAssetMapper.selectListByQuery(
                QueryWrapper.create().where(GroupAsset::getConversationId).eq(conversationId)
        );
        List<String> assetKeys = assets.stream()
                .map(GroupAsset::getFileKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        // 清理关联数据：群公告、群资产、群邀请、入群申请通知
        groupAnnouncementMapper.deleteByQuery(
                QueryWrapper.create().where(GroupAnnouncement::getConversationId).eq(conversationId)
        );
        groupAssetMapper.deleteByQuery(
                QueryWrapper.create().where(GroupAsset::getConversationId).eq(conversationId)
        );
        groupInvitationMapper.deleteByQuery(
                QueryWrapper.create().where(GroupInvitation::getConversationId).eq(conversationId)
        );
        notificationMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(MessageNotification::getType).eq(NOTIFY_TYPE_GROUP_JOIN_REQUEST)
                        .and(MessageNotification::getRelatedId).eq(conversationId)
        );

        // 逻辑删除会话消息（清空附件引用，避免残留可读内容）
        com.linkx.server.entity.ImMessage msgPatch = new com.linkx.server.entity.ImMessage();
        msgPatch.setContent(null);
        msgPatch.setFileUrl(null);
        msgPatch.setFileName(null);
        msgPatch.setDeleted(1);
        messageMapper.updateByQuery(msgPatch,
                QueryWrapper.create().where(com.linkx.server.entity.ImMessage::getConversationId).eq(conversationId));

        // 删除所有成员
        memberMapper.deleteByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );

        // 删除会话（逻辑删除）
        conversationMapper.deleteById(conversationId);

        // 事务提交后再删 MinIO，避免回滚后对象已无
        if (!assetKeys.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : assetKeys) {
                        try {
                            fileStorageService.deleteFile(key);
                        } catch (Exception e) {
                            log.warn("解散群聊删除 MinIO 对象失败: key={}, err={}", key, e.getMessage());
                        }
                    }
                }
            });
        } else {
            for (String key : assetKeys) {
                try {
                    fileStorageService.deleteFile(key);
                } catch (Exception e) {
                    log.warn("解散群聊删除 MinIO 对象失败: key={}, err={}", key, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public void transferOwner(Long userId, Long conversationId, Long newOwnerId) {
        ImConversation group = assertGroupOwner(userId, conversationId);

        // 验证新群主是否在群中
        ImConversationMember newOwnerMember = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(newOwnerId)
        );
        if (newOwnerMember == null) {
            throw new CustomException(400, "新群主必须在群中");
        }

        // 更新群主
        group.setOwnerId(newOwnerId);
        conversationMapper.update(group);

        // 更新原群主为管理员
        ImConversationMember oldOwnerMember = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (oldOwnerMember != null) {
            oldOwnerMember.setRole(ImConversationMember.ROLE_ADMIN);
            memberMapper.update(oldOwnerMember);
        }

        // 新群主设为群主角色
        newOwnerMember.setRole(ImConversationMember.ROLE_OWNER);
        memberMapper.update(newOwnerMember);

        SysUser oldOwner = sysUserMapper.selectOneById(userId);
        SysUser newOwner = sysUserMapper.selectOneById(newOwnerId);
        emitSystemTip(userId, conversationId,
                displayName(oldOwner) + " 将群主转让给了 " + displayName(newOwner));
    }

    @Override
    @Transactional
    public void updateMemberRole(Long userId, Long conversationId, Long memberId, String role) {
        assertGroupOwner(userId, conversationId);

        if (!ImConversationMember.ROLE_ADMIN.equals(role) && !ImConversationMember.ROLE_MEMBER.equals(role)) {
            throw new CustomException(400, "角色只能是管理员或普通成员");
        }
        if (Objects.equals(userId, memberId)) {
            throw new CustomException(400, "不能修改自己的角色");
        }

        ImConversationMember target = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(memberId)
        );
        if (target == null) {
            throw new CustomException(404, "该成员不在群中");
        }
        if (ImConversationMember.ROLE_OWNER.equals(target.getRole())) {
            throw new CustomException(400, "不能修改群主角色，请使用转让群主");
        }
        if (role.equals(target.getRole())) {
            return;
        }

        target.setRole(role);
        memberMapper.update(target);

        SysUser operator = sysUserMapper.selectOneById(userId);
        SysUser targetUser = sysUserMapper.selectOneById(memberId);
        if (ImConversationMember.ROLE_ADMIN.equals(role)) {
            emitSystemTip(userId, conversationId,
                    displayName(operator) + " 将 " + displayName(targetUser) + " 设为管理员");
        } else {
            emitSystemTip(userId, conversationId,
                    displayName(operator) + " 取消了 " + displayName(targetUser) + " 的管理员身份");
        }

        // 推送角色变更给被修改的成员
        Map<String, Object> roleChangeData = Map.of(
                "conversationId", String.valueOf(conversationId),
                "memberId", String.valueOf(memberId),
                "role", role
        );
        imPushService.pushToUser(memberId, "group_member_role_changed", roleChangeData);

        // 推送角色变更给所有群成员（刷新成员列表显示）
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        Map<String, Object> broadcastData = Map.of(
                "conversationId", String.valueOf(conversationId),
                "memberId", String.valueOf(memberId),
                "role", role,
                "operatorName", displayName(operator),
                "targetName", displayName(targetUser)
        );
        imPushService.pushActionToConversationMembers(conversationId, "group_member_role_changed", broadcastData);
    }

    @Override
    @Transactional
    public GroupConversationVO updateMuteAll(Long userId, Long conversationId, MuteAllDTO dto) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        if (dto == null) {
            throw new CustomException(400, "参数不能为空");
        }

        Date now = new Date();
        boolean clearSchedule = Boolean.TRUE.equals(dto.getClearSchedule());
        boolean timed = dto.getStartTime() != null && dto.getEndTime() != null;

        if (clearSchedule) {
            // 取消定时：清空计划；若当前因定时而禁言中，一并关闭全体禁言
            group.setMuteAllStart(null);
            group.setMuteAllEnd(null);
            group.setMuteAll(0);
        } else if (timed) {
            if (dto.getEndTime() <= dto.getStartTime()) {
                throw new CustomException(400, "结束时间必须晚于开始时间");
            }
            if (dto.getEndTime() <= now.getTime()) {
                throw new CustomException(400, "结束时间必须晚于当前时间");
            }
            Date start = new Date(dto.getStartTime());
            Date end = new Date(dto.getEndTime());
            group.setMuteAllStart(start);
            group.setMuteAllEnd(end);
            group.setMuteAll(!now.before(start) ? 1 : 0);
        } else if (dto.getEnabled() != null) {
            group.setMuteAll(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
            if (!Boolean.TRUE.equals(dto.getEnabled())) {
                group.setMuteAllStart(null);
                group.setMuteAllEnd(null);
            } else if (dto.getEndTime() != null) {
                if (dto.getEndTime() <= now.getTime()) {
                    throw new CustomException(400, "结束时间必须晚于当前时间");
                }
                group.setMuteAllStart(now);
                group.setMuteAllEnd(new Date(dto.getEndTime()));
            } else {
                // 手动开启：清除定时计划
                group.setMuteAllStart(null);
                group.setMuteAllEnd(null);
            }
        } else {
            throw new CustomException(400, "请指定 enabled、定时时间或取消定时");
        }

        // 必须用 UpdateChain：普通 update 会忽略 null，导致定时字段清不掉
        persistMuteAllFields(group);
        SysUser owner = sysUserMapper.selectOneById(group.getOwnerId());
        SysUser operator = sysUserMapper.selectOneById(userId);
        String opName = displayName(operator);
        if (clearSchedule) {
            emitSystemTip(userId, conversationId, opName + " 取消了定时全体禁言");
        } else if (timed) {
            emitSystemTip(userId, conversationId, opName + " 设置了定时全体禁言");
        } else if (Boolean.TRUE.equals(dto.getEnabled())) {
            emitSystemTip(userId, conversationId, opName + " 开启了全体禁言");
        } else if (Boolean.FALSE.equals(dto.getEnabled())) {
            emitSystemTip(userId, conversationId, opName + " 关闭了全体禁言");
        }

        // 推送全体禁言状态变更给所有群成员
        Map<String, Object> muteAllData = Map.of(
                "conversationId", String.valueOf(conversationId),
                "muteAll", group.getMuteAll() != null && group.getMuteAll() == 1,
                "muteAllStart", group.getMuteAllStart() != null ? group.getMuteAllStart().getTime() : null,
                "muteAllEnd", group.getMuteAllEnd() != null ? group.getMuteAllEnd().getTime() : null
        );
        imPushService.pushActionToConversationMembers(conversationId, "group_mute_all_changed", muteAllData);

        return toGroupConversationVO(group, owner, userId);
    }

    @Override
    @Transactional
    public void updateMemberMute(Long userId, Long conversationId, Long memberId, MuteMemberDTO dto) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        if (dto == null || dto.getMuted() == null) {
            throw new CustomException(400, "参数不能为空");
        }
        if (Objects.equals(group.getOwnerId(), memberId)) {
            throw new CustomException(400, "不能禁言群主");
        }
        if (Objects.equals(userId, memberId)) {
            throw new CustomException(400, "不能禁言自己");
        }

        ImConversationMember target = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(memberId)
        );
        if (target == null) {
            throw new CustomException(404, "该成员不在群中");
        }

        // 管理员不能禁言其他管理员，仅群主可以
        boolean operatorIsOwner = group.getOwnerId().equals(userId);
        if (!operatorIsOwner && ImConversationMember.ROLE_ADMIN.equals(target.getRole())) {
            throw new CustomException(403, "管理员不能禁言其他管理员");
        }

        if (Boolean.TRUE.equals(dto.getMuted())) {
            Date until = null;
            if (dto.getMuteUntil() != null) {
                if (dto.getMuteUntil() <= System.currentTimeMillis()) {
                    throw new CustomException(400, "禁言截止时间必须晚于当前时间");
                }
                until = new Date(dto.getMuteUntil());
            }
            target.setMuted(1);
            target.setMuteUntil(until);
        } else {
            target.setMuted(0);
            target.setMuteUntil(null);
        }
        memberMapper.update(target);

        SysUser operator = sysUserMapper.selectOneById(userId);
        SysUser targetUser = sysUserMapper.selectOneById(memberId);
        if (Boolean.TRUE.equals(dto.getMuted())) {
            emitSystemTip(userId, conversationId,
                    displayName(operator) + " 将 " + displayName(targetUser) + " 禁言");
        } else {
            emitSystemTip(userId, conversationId,
                    displayName(operator) + " 解除了 " + displayName(targetUser) + " 的禁言");
        }

        // 推送禁言状态变更给被禁言的成员
        // 注意：Map.of 不允许 null value，muteUntil 可能为 null，改用 HashMap
        Map<String, Object> muteData = new java.util.HashMap<>();
        muteData.put("conversationId", String.valueOf(conversationId));
        muteData.put("memberId", String.valueOf(memberId));
        muteData.put("muted", Boolean.TRUE.equals(dto.getMuted()));
        muteData.put("muteUntil", dto.getMuteUntil());
        imPushService.pushToUser(memberId, "group_mute_changed", muteData);
    }

    @Override
    @Transactional
    public void applyMuteSchedules() {
        Date now = new Date();

        // 定时开始：到点开启全体禁言
        List<ImConversation> toStart = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getType).eq(ImConversation.TYPE_GROUP)
                        .and(ImConversation::getMuteAllStart).le(now)
                        .and(ImConversation::getMuteAll).eq(0)
                        .and(ImConversation::getMuteAllStart).isNotNull()
        );
        for (ImConversation g : toStart) {
            // 若已过结束时间则直接清理
            if (g.getMuteAllEnd() != null && !now.before(g.getMuteAllEnd())) {
                g.setMuteAll(0);
                g.setMuteAllStart(null);
                g.setMuteAllEnd(null);
            } else {
                g.setMuteAll(1);
            }
            persistMuteAllFields(g);
        }

        // 定时结束：到点关闭全体禁言并清空计划
        List<ImConversation> toEnd = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversation::getType).eq(ImConversation.TYPE_GROUP)
                        .and(ImConversation::getMuteAllEnd).le(now)
                        .and(ImConversation::getMuteAllEnd).isNotNull()
        );
        for (ImConversation g : toEnd) {
            g.setMuteAll(0);
            g.setMuteAllStart(null);
            g.setMuteAllEnd(null);
            persistMuteAllFields(g);
        }

        // 成员定时禁言到期
        List<ImConversationMember> expired = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getMuted).eq(1)
                        .and(ImConversationMember::getMuteUntil).le(now)
                        .and(ImConversationMember::getMuteUntil).isNotNull()
        );
        for (ImConversationMember m : expired) {
            m.setMuted(0);
            m.setMuteUntil(null);
            memberMapper.update(m);
        }
    }

    @Override
    @Transactional
    public String updateMyRemark(Long userId, Long conversationId, String remark) {
        assertGroupMember(userId, conversationId);
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "你不是该群成员");
        }
        String value = remark == null ? "" : remark.trim();
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        member.setRemark(value.isEmpty() ? null : value);
        memberMapper.update(member);
        return member.getRemark() == null ? "" : member.getRemark();
    }

    // ==================== 私有方法 ====================

    private void assertGroupMember(Long userId, Long conversationId) {
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "你不是该群成员");
        }
    }

    private ImConversation assertGroupOwner(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        if (!Objects.equals(group.getOwnerId(), userId)) {
            throw new CustomException(403, "只有群主才能执行此操作");
        }
        assertGroupMember(userId, conversationId);
        return group;
    }

    private ImConversation assertGroupAdmin(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        assertGroupMember(userId, conversationId);

        // 群主也算管理员
        if (Objects.equals(group.getOwnerId(), userId)) {
            return group;
        }

        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null || !ImConversationMember.ROLE_ADMIN.equals(member.getRole())) {
            throw new CustomException(403, "只有群主或管理员才能执行此操作");
        }
        return group;
    }

    /**
     * 群邀请策略校验：
     * - anyMember：任意成员均可邀请（不限制）；
     * - ownerApprove：仅群主/管理员可邀请；
     * - 未知策略：fail-safe 拒绝（防 fail-open 默认放行漏洞）。
     */
    private void enforceInvitePolicy(Long userId, ImConversation conversation) {
        String policy = conversation.getInvitePolicy();
        if (policy == null || policy.isBlank()) {
            // 未显式配置：默认 anyMember，保持与历史行为兼容
            return;
        }
        if (!"anyMember".equals(policy) && !"ownerApprove".equals(policy)) {
            // 未知策略：fail-safe 拒绝，避免放行错误配置
            throw new CustomException(500, "未知的群邀请策略：" + policy);
        }
        if ("anyMember".equals(policy)) {
            return;
        }
        // ownerApprove：仅群主/管理员可邀请
        if (Objects.equals(conversation.getOwnerId(), userId)) {
            return;
        }
        ImConversationMember m = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversation.getId())
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (m == null || !ImConversationMember.ROLE_ADMIN.equals(m.getRole())) {
            throw new CustomException(403, "当前群聊仅群主或管理员可邀请成员");
        }
    }

    private GroupConversationVO toGroupConversationVO(ImConversation group, SysUser owner, Long viewerUserId) {
        // 统计成员数量
        long memberCount = memberMapper.selectCountByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(group.getId())
        );

        Date now = new Date();
        String myRemark = null;
        boolean meMuted = false;
        Long meMuteUntil = null;
        if (viewerUserId != null) {
            ImConversationMember me = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(group.getId())
                            .and(ImConversationMember::getUserId).eq(viewerUserId)
            );
            if (me != null) {
                myRemark = me.getRemark();
                meMuted = isMemberMuteActive(me, now);
                meMuteUntil = me.getMuteUntil() != null ? me.getMuteUntil().getTime() : null;
            }
        }

        boolean muteAllActive = isMuteAllActive(group, now);
        // 仅下发未结束的定时计划；已过期的 start/end 不返回，避免前端开关关着仍显示「定时」
        Long scheduleStart = null;
        Long scheduleEnd = null;
        if (group.getMuteAllEnd() != null && now.before(group.getMuteAllEnd())) {
            scheduleStart = group.getMuteAllStart() != null ? group.getMuteAllStart().getTime() : null;
            scheduleEnd = group.getMuteAllEnd().getTime();
        } else if (group.getMuteAllEnd() != null && !now.before(group.getMuteAllEnd())) {
            // 读时懒清理：定时已结束但 cron 尚未扫到
            group.setMuteAll(0);
            group.setMuteAllStart(null);
            group.setMuteAllEnd(null);
            persistMuteAllFields(group);
        }
        String signedAvatar = mediaUrlService.resolve(group.getAvatar());
        List<GroupMemberAvatarVO> memberAvatars = loadGroupMemberAvatarPreviews(Set.of(group.getId()))
                .getOrDefault(group.getId(), List.of());
        return GroupConversationVO.builder()
                .id(group.getId())
                .type(group.getType())
                .name(group.getName())
                .avatar(signedAvatar)
                .memberAvatars(memberAvatars)
                .announcement(group.getAnnouncement())
                .ownerId(group.getOwnerId())
                .ownerNickname(owner != null ? owner.getNickname() : null)
                .memberCount((int) memberCount)
                .lastMessage(group.getLastMessageContent())
                .lastMessageTime(group.getLastMessageTime() != null ? group.getLastMessageTime().getTime() : null)
                .myRemark(myRemark)
                .muteAll(muteAllActive)
                .muteAllStart(scheduleStart)
                .muteAllEnd(scheduleEnd)
                .meMuted(meMuted)
                .meMuteUntil(meMuteUntil)
                .joinApproval(group.getJoinApproval() != null && group.getJoinApproval() == 1)
                .invitePolicy(group.getInvitePolicy() != null ? group.getInvitePolicy() : "anyMember")
                .build();
    }

    /** 全体禁言是否生效（考虑定时窗口） */
    static boolean isMuteAllActive(ImConversation group, Date now) {
        if (group == null) return false;
        Date start = group.getMuteAllStart();
        Date end = group.getMuteAllEnd();
        if (end != null && !now.before(end)) {
            return false;
        }
        if (start != null && end != null && !now.before(start) && now.before(end)) {
            return true;
        }
        return Integer.valueOf(1).equals(group.getMuteAll());
    }

    /** 成员个人禁言是否生效 */
    static boolean isMemberMuteActive(ImConversationMember member, Date now) {
        if (member == null || !Integer.valueOf(1).equals(member.getMuted())) {
            return false;
        }
        if (member.getMuteUntil() != null && !now.before(member.getMuteUntil())) {
            return false;
        }
        return true;
    }

    /**
     * 持久化全体禁言相关字段。
     * <p>
     * 使用 UpdateChain 显式写入，确保 muteAllStart/muteAllEnd 可被置为 NULL
     * （BaseMapper.update 默认忽略 null，会导致「取消定时」后库里仍残留计划）。
     * </p>
     */
    private void persistMuteAllFields(ImConversation group) {
        UpdateChain.of(ImConversation.class)
                .set(ImConversation::getMuteAll, group.getMuteAll() != null ? group.getMuteAll() : 0)
                .set(ImConversation::getMuteAllStart, group.getMuteAllStart())
                .set(ImConversation::getMuteAllEnd, group.getMuteAllEnd())
                .where(ImConversation::getId).eq(group.getId())
                .update();
    }

    private void emitSystemTip(Long operatorId, Long conversationId, String content) {
        Runnable task = () -> {
            try {
                MessageVO tip = chatService.postSystemMessage(operatorId, conversationId, content);
                imPushService.pushToConversationMembers(tip, operatorId, null);
            } catch (Exception e) {
                // 系统提示失败不影响主业务
                log.warn("emitSystemTip failed: {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 推送群信息变更给所有群成员。
     * @param conversationId 群会话 ID
     * @param action 事件类型：group_renamed / group_announcement_updated
     * @param groupVO 更新后的群信息
     */
    private void emitGroupUpdate(Long conversationId, String action, GroupConversationVO groupVO) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        Map<String, Object> data = Map.of(
                "conversationId", String.valueOf(conversationId),
                "group", groupVO
        );
        for (ImConversationMember member : members) {
            imPushService.pushToUser(member.getUserId(), action, data);
        }
    }

    private String displayName(SysUser user) {
        if (user == null) return "用户";
        if (StringUtils.hasText(user.getNickname())) return user.getNickname();
        if (StringUtils.hasText(user.getUsername())) return user.getUsername();
        return "用户";
    }

    private ConversationVO toConversationVO(ImConversation group, List<GroupMemberAvatarVO> memberAvatars) {
        String signedAvatar = mediaUrlService.resolve(group.getAvatar());
        return ConversationVO.builder()
                .id(group.getId())
                .type(group.getType())
                .name(group.getName())
                .avatar(signedAvatar)
                .memberAvatars(memberAvatars)
                .peerNickname(group.getName())
                .peerAvatar(signedAvatar)
                .lastMessage(group.getLastMessageContent())
                .lastMessageTime(group.getLastMessageTime() != null ? group.getLastMessageTime().getTime() : null)
                .build();
    }

    /**
     * 批量加载群成员头像预览（每群最多 9 人）。
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

    // ==================== 群成员批量管理 ====================

    @Override
    @Transactional
    public void batchRemoveMembers(Long userId, Long conversationId, List<Long> memberIds) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        if (memberIds == null || memberIds.isEmpty()) {
            throw new CustomException(400, "成员列表不能为空");
        }
        boolean operatorIsOwner = Objects.equals(group.getOwnerId(), userId);
        int removed = 0;
        for (Long memberId : memberIds) {
            if (memberId.equals(userId)) continue;
            if (Objects.equals(group.getOwnerId(), memberId)) continue;
            ImConversationMember target = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getUserId).eq(memberId)
            );
            if (target != null) {
                // 非群主不能移除管理员：仅群主可对其他管理员操作
                if (!operatorIsOwner && ImConversationMember.ROLE_ADMIN.equals(target.getRole())) {
                    throw new CustomException(403, "管理员不能移除其他管理员");
                }
                memberMapper.deleteById(target.getId());
                removed++;
            }
        }
        if (removed > 0) {
            SysUser operator = sysUserMapper.selectOneById(userId);
            emitSystemTip(userId, conversationId, displayName(operator) + " 批量移除了 " + removed + " 名成员");
        }
    }

    @Override
    @Transactional
    public void batchMuteMembers(Long userId, Long conversationId, List<Long> memberIds, boolean muted) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        if (memberIds == null || memberIds.isEmpty()) {
            throw new CustomException(400, "成员列表不能为空");
        }
        boolean operatorIsOwner = Objects.equals(group.getOwnerId(), userId);
        int affected = 0;
        for (Long memberId : memberIds) {
            if (memberId.equals(userId)) continue;
            if (Objects.equals(group.getOwnerId(), memberId)) continue;
            ImConversationMember target = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getUserId).eq(memberId)
            );
            if (target != null) {
                // 非群主不能禁言管理员：与单条 updateMemberMute 行为保持一致
                if (!operatorIsOwner && ImConversationMember.ROLE_ADMIN.equals(target.getRole())) {
                    throw new CustomException(403, "管理员不能禁言其他管理员");
                }
                target.setMuted(muted ? 1 : 0);
                target.setMuteUntil(null);
                memberMapper.update(target);
                affected++;
            }
        }
        if (affected > 0) {
            SysUser operator = sysUserMapper.selectOneById(userId);
            String action = muted ? "禁言" : "解禁";
            emitSystemTip(userId, conversationId, displayName(operator) + " 批量" + action + "了 " + affected + " 名成员");
        }
    }

    // ==================== 入群审核 ====================

    @Override
    @Transactional
    public void setJoinApproval(Long userId, Long conversationId, boolean required) {
        ImConversation group = assertGroupOwner(userId, conversationId);
        group.setJoinApproval(required ? 1 : 0);
        conversationMapper.update(group);
    }

    @Override
    @Transactional
    public void handleJoinRequest(Long userId, Long conversationId, Long applicantId, boolean approve) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        ImConversationMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(applicantId)
        );
        if (existing != null && existing.getDeleted() == 0) {
            throw new CustomException(400, "该用户已是群成员");
        }
        if (approve) {
            // 复用 ensureActiveMembership 处理退群重入：恢复软删除行或新建，避免唯一键冲突
            ensureActiveMembership(conversationId, applicantId);
            SysUser applicant = sysUserMapper.selectOneById(applicantId);
            emitSystemTip(userId, conversationId, displayName(applicant) + " 已加入群聊");
            imPushService.pushToUser(applicantId, "notification_refresh", Map.of(
                    "type", "group_join_approved",
                    "conversationId", String.valueOf(conversationId),
                    "groupName", group.getName() != null ? group.getName() : "群聊"
            ));
        } else {
            SysUser operator = sysUserMapper.selectOneById(userId);
            SysUser applicant = sysUserMapper.selectOneById(applicantId);
            emitSystemTip(userId, conversationId, displayName(operator) + " 拒绝了 " + displayName(applicant) + " 的入群申请");
            imPushService.pushToUser(applicantId, "notification_refresh", Map.of(
                    "type", "group_join_rejected",
                    "conversationId", String.valueOf(conversationId),
                    "groupName", group.getName() != null ? group.getName() : "群聊"
            ));
        }
        markJoinRequestNotificationsRead(conversationId, applicantId);
    }

    @Override
    @Transactional
    public void requestJoin(Long userId, Long conversationId, String message) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        ImConversationMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (existing != null && existing.getDeleted() == 0) {
            throw new CustomException(400, "你已是群成员");
        }
        if (group.getJoinApproval() == null || group.getJoinApproval() == 0) {
            // 无需审批，直接加入；复用 ensureActiveMembership 处理退群重入，避免唯一键冲突
            ensureActiveMembership(conversationId, userId);
            SysUser user = sysUserMapper.selectOneById(userId);
            emitSystemTip(userId, conversationId, displayName(user) + " 加入了群聊");
        } else {
            // 需审批：通知群主/管理员，并写入可审批的通知记录
            SysUser user = sysUserMapper.selectOneById(userId);
            String tip = displayName(user) + " 申请加入群聊"
                    + (message != null && !message.isBlank() ? "：" + message : "");
            String content = (message != null && !message.isBlank()) ? message.trim() : "申请加入群聊";
            List<ImConversationMember> admins = memberMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getDeleted).eq(0)
                            .and(ImConversationMember::getRole).in(
                                    ImConversationMember.ROLE_OWNER,
                                    ImConversationMember.ROLE_ADMIN
                            )
            );
            String avatar = mediaUrlService.resolve(user != null ? user.getAvatar() : null);
            for (ImConversationMember admin : admins) {
                // 去重：同一申请人未读申请不重复刷
                long pending = notificationMapper.selectCountByQuery(
                        QueryWrapper.create()
                                .where(MessageNotification::getUserId).eq(admin.getUserId())
                                .and(MessageNotification::getType).eq(NOTIFY_TYPE_GROUP_JOIN_REQUEST)
                                .and(MessageNotification::getRelatedId).eq(conversationId)
                                .and(MessageNotification::getSenderId).eq(userId)
                                .and(MessageNotification::getReadStatus).eq(0)
                );
                if (pending > 0) {
                    continue;
                }
                notificationService.create(
                        admin.getUserId(),
                        userId,
                        displayName(user),
                        avatar,
                        NOTIFY_TYPE_GROUP_JOIN_REQUEST,
                        conversationId,
                        content
                );
                imPushService.pushToUser(admin.getUserId(), "notification_refresh", Map.of(
                        "type", NOTIFY_TYPE_GROUP_JOIN_REQUEST,
                        "conversationId", String.valueOf(conversationId),
                        "applicantId", String.valueOf(userId)
                ));
            }
            // 申请人尚非成员，系统提示用群主身份落库
            Long tipSender = group.getOwnerId() != null ? group.getOwnerId() : admins.stream()
                    .map(ImConversationMember::getUserId)
                    .findFirst()
                    .orElse(userId);
            emitSystemTip(tipSender, conversationId, tip);
        }
    }

    @Override
    public List<GroupJoinRequestVO> listJoinRequests(Long userId, Long conversationId) {
        assertGroupAdmin(userId, conversationId);
        List<MessageNotification> list = notificationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(MessageNotification::getUserId).eq(userId)
                        .and(MessageNotification::getType).eq(NOTIFY_TYPE_GROUP_JOIN_REQUEST)
                        .and(MessageNotification::getRelatedId).eq(conversationId)
                        .and(MessageNotification::getReadStatus).eq(0)
                        .orderBy(MessageNotification::getCreateTime, false)
        );
        List<GroupJoinRequestVO> result = new ArrayList<>();
        for (MessageNotification n : list) {
            if (n.getSenderId() == null) continue;
            result.add(GroupJoinRequestVO.builder()
                    .applicantId(n.getSenderId())
                    .applicantNickname(n.getSenderName())
                    .applicantAvatar(mediaUrlService.resolve(n.getSenderAvatar()))
                    .message(n.getContent())
                    .createTime(n.getCreateTime() != null ? n.getCreateTime().getTime() : null)
                    .notificationId(n.getId())
                    .build());
        }
        return result;
    }

    private void markJoinRequestNotificationsRead(Long conversationId, Long applicantId) {
        List<MessageNotification> list = notificationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(MessageNotification::getType).eq(NOTIFY_TYPE_GROUP_JOIN_REQUEST)
                        .and(MessageNotification::getRelatedId).eq(conversationId)
                        .and(MessageNotification::getSenderId).eq(applicantId)
                        .and(MessageNotification::getReadStatus).eq(0)
        );
        for (MessageNotification n : list) {
            n.setReadStatus(1);
            notificationMapper.update(n);
        }
    }

    // ==================== 群公告已读统计 ====================

    @Override
    @Transactional
    public void markAnnouncementRead(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member != null) {
            member.setAnnouncementRead(true);
            memberMapper.update(member);
        }
    }

    @Override
    public long getAnnouncementReadCount(Long userId, Long conversationId) {
        // 必须为群成员，防 IDOR 越权探测任意群公告已读数
        assertGroupMember(userId, conversationId);
        return memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getAnnouncementRead).eq(true)
        );
    }

    // ==================== 群聊邀请策略 ====================

    @Override
    @Transactional
    public void setInvitePolicy(Long userId, Long conversationId, String policy) {
        ImConversation group = assertGroupOwner(userId, conversationId);
        if (!"ownerApprove".equals(policy) && !"anyMember".equals(policy)) {
            throw new CustomException(400, "策略只能是 ownerApprove 或 anyMember");
        }
        group.setInvitePolicy(policy);
        conversationMapper.update(group);
    }
}
