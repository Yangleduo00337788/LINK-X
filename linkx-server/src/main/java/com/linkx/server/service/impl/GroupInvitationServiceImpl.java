package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.InviteGroupDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupInvitationVO;
import com.linkx.server.controller.vo.GroupMemberAvatarVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.GroupInvitation;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.GroupInvitationService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInvitationServiceImpl implements GroupInvitationService {

    private final GroupInvitationMapper invitationMapper;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final ImMessagePushService imPushService;
    private final MediaUrlService mediaUrlService;
    private final ChatService chatService;

    @Override
    @Transactional
    public GroupInvitationVO invite(Long userId, Long conversationId, InviteGroupDTO dto) {
        ImConversation conversation = requireGroup(userId, conversationId);
        enforceInvitePolicy(userId, conversation);

        // 不能邀请已经是成员的用户
        ImConversationMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(dto.getInviteeUserId())
        );
        if (existing != null) {
            throw new CustomException(400, "该用户已在群聊中");
        }

        // 同一 inviter -> invitee -> conversation 只保留一条 pending 记录
        GroupInvitation pending = invitationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(GroupInvitation::getConversationId).eq(conversationId)
                        .and(GroupInvitation::getInviteeUserId).eq(dto.getInviteeUserId())
                        .and(GroupInvitation::getStatus).eq(GroupInvitation.STATUS_PENDING)
        );
        if (pending != null) {
            // 已有 pending 邀请：刷新留言，保持幂等
            if (dto.getMessage() != null && !dto.getMessage().isBlank()) {
                pending.setMessage(dto.getMessage());
                invitationMapper.update(pending);
            }
            return toVO(pending, conversation);
        }

        GroupInvitation inv = GroupInvitation.builder()
                .conversationId(conversationId)
                .inviterUserId(userId)
                .inviteeUserId(dto.getInviteeUserId())
                .message(dto.getMessage())
                .status(GroupInvitation.STATUS_PENDING)
                .build();
        invitationMapper.insert(inv);
        imPushService.pushToUser(dto.getInviteeUserId(), "notification_refresh", Map.of("type", "group_invitation"));
        return toVO(inv, conversation);
    }

    @Override
    public List<GroupInvitationVO> listMyInvitations(Long userId) {
        List<GroupInvitation> list = invitationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupInvitation::getInviteeUserId).eq(userId)
                        .orderBy(GroupInvitation::getCreateTime, false)
        );
        if (list.isEmpty()) return List.of();
        Set<Long> convIds = list.stream().map(GroupInvitation::getConversationId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> userIds = list.stream().map(GroupInvitation::getInviterUserId).collect(Collectors.toCollection(HashSet::new));
        java.util.Map<Long, ImConversation> convMap = new java.util.HashMap<>();
        for (ImConversation c : conversationMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversation::getId).in(convIds))) {
            convMap.put(c.getId(), c);
        }
        java.util.Map<Long, SysUser> userMap = new java.util.HashMap<>();
        for (SysUser u : userMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds))) {
            userMap.put(u.getId(), u);
        }
        List<GroupInvitationVO> result = new ArrayList<>(list.size());
        for (GroupInvitation inv : list) {
            ImConversation conv = convMap.get(inv.getConversationId());
            SysUser inviter = userMap.get(inv.getInviterUserId());
            GroupInvitationVO vo = toVO(inv, conv);
            if (inviter != null) {
                vo.setInviterNickname(inviter.getNickname());
                vo.setInviterAvatar(mediaUrlService.resolve(inviter.getAvatar()));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public GroupConversationVO accept(Long userId, Long invitationId) {
        GroupInvitation inv = requirePendingInvitation(userId, invitationId);
        ImConversation conversation = requireGroup(inv.getConversationId());
        ensureInviteeMembership(inv.getConversationId(), userId);
        // uk: (conversation, invitee, status) — 清掉历史 accepted 再改状态，避免二次接受冲突
        clearInvitationsWithStatus(inv.getConversationId(), userId, GroupInvitation.STATUS_ACCEPTED, inv.getId());
        inv.setStatus(GroupInvitation.STATUS_ACCEPTED);
        invitationMapper.update(inv);

        // 获取被接受者的信息
        SysUser invitee = userMapper.selectOneById(userId);

        // 推送群会话信息给被接受者（让其可以直接加入会话列表）
        GroupConversationVO groupVO = toGroupVO(conversation);
        imPushService.pushToUser(userId, "group_added", Map.of(
                "conversationId", String.valueOf(conversation.getId()),
                "group", groupVO
        ));

        // 推送系统消息，通知其他群成员有人加入了群
        if (invitee != null) {
            String inviteeName = invitee.getNickname() != null ? invitee.getNickname() : invitee.getUsername();
            emitSystemTip(conversation.getId(), inviteeName + " 加入了群聊");
        }

        // 推送通知刷新给邀请人
        imPushService.pushToUser(inv.getInviterUserId(), "notification_refresh", Map.of(
                "type", "group_join_approved",
                "conversationId", String.valueOf(conversation.getId())
        ));

        // 推送 group_member_added 事件给所有群成员，刷新成员列表
        if (invitee != null) {
            String inviteeName = invitee.getNickname() != null ? invitee.getNickname() : invitee.getUsername();
            Map<String, Object> memberData = Map.of(
                    "conversationId", String.valueOf(conversation.getId()),
                    "memberId", String.valueOf(userId),
                    "memberName", inviteeName,
                    "memberAvatar", mediaUrlService.resolve(invitee.getAvatar())
            );
            // 推送给除新成员外的所有群成员
            List<ImConversationMember> members = memberMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversation.getId())
                            .and(ImConversationMember::getUserId).ne(userId)
            );
            for (ImConversationMember member : members) {
                imPushService.pushToUser(member.getUserId(), "group_member_added", memberData);
            }
        }

        return groupVO;
    }

    /**
     * 向群会话的所有在线成员发送系统消息。
     */
    private void emitSystemTip(Long conversationId, String content) {
        try {
            ImConversation conversation = conversationMapper.selectOneById(conversationId);
            if (conversation == null) return;
            // 使用群主 ID 作为发送者
            Long senderId = conversation.getOwnerId() != null ? conversation.getOwnerId() : 1L;
            MessageVO tip = chatService.postSystemMessage(senderId, conversationId, content);
            imPushService.pushToConversationMembers(tip, senderId, null);
        } catch (Exception e) {
            log.warn("emitSystemTip failed: {}", e.toString());
        }
    }

    @Override
    @Transactional
    public void reject(Long userId, Long invitationId) {
        GroupInvitation inv = requirePendingInvitation(userId, invitationId);
        // 同上：历史 rejected 行会挡住 pending→rejected 更新
        clearInvitationsWithStatus(inv.getConversationId(), userId, GroupInvitation.STATUS_REJECTED, inv.getId());
        inv.setStatus(GroupInvitation.STATUS_REJECTED);
        invitationMapper.update(inv);
    }

    private void clearInvitationsWithStatus(Long conversationId, Long inviteeUserId, int status, Long keepId) {
        List<GroupInvitation> old = invitationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupInvitation::getConversationId).eq(conversationId)
                        .and(GroupInvitation::getInviteeUserId).eq(inviteeUserId)
                        .and(GroupInvitation::getStatus).eq(status)
        );
        for (GroupInvitation row : old) {
            if (keepId != null && keepId.equals(row.getId())) {
                continue;
            }
            invitationMapper.deleteById(row.getId());
        }
    }

    /**
     * 接受邀请时写入/恢复成员行。退群是逻辑删除且 uk_conv_user 仍占用，需恢复而非再 insert。
     */
    private void ensureInviteeMembership(Long conversationId, Long userId) {
        ImConversationMember active = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (active != null) {
            return;
        }
        ImConversationMember softDeleted = LogicDeleteManager.execWithoutLogicDelete(() ->
                memberMapper.selectOneByQuery(
                        QueryWrapper.create()
                                .where(ImConversationMember::getConversationId).eq(conversationId)
                                .and(ImConversationMember::getUserId).eq(userId)
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
            return;
        }
        memberMapper.insert(ImConversationMember.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(ImConversationMember.ROLE_MEMBER)
                .muted(0)
                .deleted(0)
                .build());
    }

    // ---------- helpers ----------

    private ImConversation requireGroup(Long userId, Long conversationId) {
        ImConversation conv = conversationMapper.selectOneById(conversationId);
        if (conv == null) {
            throw new CustomException(404, "群聊不存在");
        }
        if (conv.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(400, "该会话不是群聊");
        }
        // 邀请人必须是群成员（owner/admin/member 都允许邀请）
        ImConversationMember m = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (m == null) {
            throw new CustomException(403, "无权邀请成员加入该群聊");
        }
        return conv;
    }

    /**
     * ownerApprove：仅群主/管理员可邀请；anyMember：任意成员可邀请。
     */
    private void enforceInvitePolicy(Long userId, ImConversation conversation) {
        String policy = conversation.getInvitePolicy();
        if (policy == null || policy.isBlank() || "anyMember".equals(policy)) {
            return;
        }
        if (!"ownerApprove".equals(policy)) {
            return;
        }
        ImConversationMember m = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversation.getId())
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (m == null) {
            throw new CustomException(403, "无权邀请成员加入该群聊");
        }
        String role = m.getRole();
        if (!ImConversationMember.ROLE_OWNER.equals(role) && !ImConversationMember.ROLE_ADMIN.equals(role)) {
            throw new CustomException(403, "当前群聊仅群主或管理员可邀请成员");
        }
    }

    private ImConversation requireGroup(Long conversationId) {
        ImConversation conv = conversationMapper.selectOneById(conversationId);
        if (conv == null) {
            throw new CustomException(404, "群聊不存在");
        }
        if (conv.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(400, "该会话不是群聊");
        }
        return conv;
    }

    private GroupInvitation requirePendingInvitation(Long userId, Long invitationId) {
        GroupInvitation inv = invitationMapper.selectOneById(invitationId);
        if (inv == null) {
            throw new CustomException(404, "邀请不存在");
        }
        if (!inv.getInviteeUserId().equals(userId)) {
            throw new CustomException(403, "无权操作该邀请");
        }
        if (inv.getStatus() != GroupInvitation.STATUS_PENDING) {
            throw new CustomException(400, "邀请已被处理");
        }
        return inv;
    }

    private GroupInvitationVO toVO(GroupInvitation inv, ImConversation conv) {
        GroupInvitationVO.GroupInvitationVOBuilder b = GroupInvitationVO.builder()
                .id(inv.getId())
                .conversationId(inv.getConversationId())
                .groupName(conv != null ? conv.getName() : null)
                .inviterUserId(inv.getInviterUserId())
                .message(inv.getMessage())
                .status(inv.getStatus());
        if (inv.getCreateTime() != null) b.createTime(inv.getCreateTime().getTime());
        return b.build();
    }

    private GroupConversationVO toGroupVO(ImConversation conv) {
        List<GroupMemberAvatarVO> memberAvatars = List.of();
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conv.getId())
        );
        if (!memberships.isEmpty()) {
            Set<Long> userIds = memberships.stream().map(ImConversationMember::getUserId).collect(Collectors.toSet());
            Map<Long, SysUser> userMap = userMapper.selectListByQuery(
                    QueryWrapper.create().where(SysUser::getId).in(userIds)
            ).stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            memberAvatars = memberships.stream()
                    .sorted(Comparator
                            .comparingInt((ImConversationMember m) -> {
                                if (ImConversationMember.ROLE_OWNER.equals(m.getRole())) return 0;
                                if (ImConversationMember.ROLE_ADMIN.equals(m.getRole())) return 1;
                                return 2;
                            })
                            .thenComparing(m -> m.getCreateTime() != null ? m.getCreateTime().getTime() : 0L))
                    .limit(9)
                    .map(m -> {
                        SysUser u = userMap.get(m.getUserId());
                        if (u == null) return null;
                        String nick = u.getNickname() != null && !u.getNickname().isBlank()
                                ? u.getNickname() : u.getUsername();
                        return GroupMemberAvatarVO.builder()
                                .nickname(nick)
                                .avatar(mediaUrlService.resolve(u.getAvatar()))
                                .build();
                    })
                    .filter(v -> v != null)
                    .collect(Collectors.toList());
        }
        return GroupConversationVO.builder()
                .id(conv.getId())
                .type(conv.getType())
                .name(conv.getName())
                .avatar(mediaUrlService.resolve(conv.getAvatar()))
                .memberAvatars(memberAvatars)
                .announcement(conv.getAnnouncement())
                .ownerId(conv.getOwnerId())
                .build();
    }
}
