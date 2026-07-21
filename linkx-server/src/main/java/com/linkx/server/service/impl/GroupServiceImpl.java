package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupMemberAvatarVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群聊服务实现
 */
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final MediaUrlService mediaUrlService;

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

        // 创建群会话
        ImConversation group = ImConversation.builder()
                .type(ImConversation.TYPE_GROUP)
                .name(dto.getName())
                .ownerId(userId)
                .build();
        conversationMapper.insert(group);

        // 添加创建者为群主
        memberMapper.insert(ImConversationMember.builder()
                .conversationId(group.getId())
                .userId(userId)
                .role(ImConversationMember.ROLE_OWNER)
                .build());

        // 添加其他成员
        for (Long memberId : dto.getMemberIds()) {
            memberMapper.insert(ImConversationMember.builder()
                    .conversationId(group.getId())
                    .userId(memberId)
                    .role(ImConversationMember.ROLE_MEMBER)
                    .build());
        }

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

        if (rename) {
            group.setName(dto.getName());
        }
        if (updateAnnouncement) {
            group.setAnnouncement(dto.getAnnouncement());
        }
        conversationMapper.update(group);

        SysUser owner = sysUserMapper.selectOneById(group.getOwnerId());
        return toGroupConversationVO(group, owner, userId);
    }

    @Override
    public List<GroupMemberVO> listMembers(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);

        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );

        Set<Long> userIds = members.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());

        Map<Long, SysUser> userMap = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds)
        ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<GroupMemberVO> result = new ArrayList<>();
        for (ImConversationMember member : members) {
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

    @Override
    @Transactional
    public List<GroupMemberVO> addMembers(Long userId, Long conversationId, AddGroupMembersDTO dto) {
        assertGroupAdmin(userId, conversationId);

        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }

        // 获取已存在的成员
        List<ImConversationMember> existingMembers = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        Set<Long> existingUserIds = existingMembers.stream()
                .map(ImConversationMember::getUserId)
                .collect(Collectors.toSet());

        List<ImConversationMember> addedMembers = new ArrayList<>();
        for (Long memberId : dto.getMemberIds()) {
            if (existingUserIds.contains(memberId)) {
                continue;
            }
            ImConversationMember newMember = ImConversationMember.builder()
                    .conversationId(conversationId)
                    .userId(memberId)
                    .role(ImConversationMember.ROLE_MEMBER)
                    .build();
            memberMapper.insert(newMember);
            addedMembers.add(newMember);
        }

        // 返回新添加的成员信息
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

    @Override
    @Transactional
    public void removeMember(Long userId, Long conversationId, Long memberId) {
        ImConversation group = assertGroupAdmin(userId, conversationId);

        // 不能移除群主
        if (group.getOwnerId().equals(memberId)) {
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

        // 删除所有成员
        memberMapper.deleteByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );

        // 删除会话（逻辑删除）
        conversationMapper.deleteById(conversationId);
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
        if (!group.getOwnerId().equals(userId)) {
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
        if (group.getOwnerId().equals(userId)) {
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

    private GroupConversationVO toGroupConversationVO(ImConversation group, SysUser owner, Long viewerUserId) {
        // 统计成员数量
        long memberCount = memberMapper.selectCountByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(group.getId())
        );

        String myRemark = null;
        if (viewerUserId != null) {
            ImConversationMember me = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(group.getId())
                            .and(ImConversationMember::getUserId).eq(viewerUserId)
            );
            if (me != null) {
                myRemark = me.getRemark();
            }
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
                .build();
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
}
