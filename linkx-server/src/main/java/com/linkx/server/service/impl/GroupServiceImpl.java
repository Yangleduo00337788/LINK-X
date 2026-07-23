package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.MuteAllDTO;
import com.linkx.server.controller.dto.MuteMemberDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupMemberAvatarVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.MediaUrlService;
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
        if (userId.equals(memberId)) {
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
        return toGroupConversationVO(group, owner, userId);
    }

    @Override
    @Transactional
    public void updateMemberMute(Long userId, Long conversationId, Long memberId, MuteMemberDTO dto) {
        ImConversation group = assertGroupAdmin(userId, conversationId);
        if (dto == null || dto.getMuted() == null) {
            throw new CustomException(400, "参数不能为空");
        }
        if (group.getOwnerId().equals(memberId)) {
            throw new CustomException(400, "不能禁言群主");
        }
        if (userId.equals(memberId)) {
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
        int removed = 0;
        for (Long memberId : memberIds) {
            if (memberId.equals(userId)) continue;
            if (group.getOwnerId().equals(memberId)) continue;
            ImConversationMember target = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getUserId).eq(memberId)
            );
            if (target != null) {
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
        int affected = 0;
        for (Long memberId : memberIds) {
            if (memberId.equals(userId)) continue;
            if (group.getOwnerId().equals(memberId)) continue;
            ImConversationMember target = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getUserId).eq(memberId)
            );
            if (target != null) {
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
            if (existing != null) {
                existing.setDeleted(0);
                memberMapper.update(existing);
            } else {
                memberMapper.insert(ImConversationMember.builder()
                        .conversationId(conversationId)
                        .userId(applicantId)
                        .role(ImConversationMember.ROLE_MEMBER)
                        .muted(0)
                        .deleted(0)
                        .build());
            }
            SysUser applicant = sysUserMapper.selectOneById(applicantId);
            emitSystemTip(userId, conversationId, displayName(applicant) + " 已加入群聊");
        } else {
            SysUser operator = sysUserMapper.selectOneById(userId);
            SysUser applicant = sysUserMapper.selectOneById(applicantId);
            emitSystemTip(userId, conversationId, displayName(operator) + " 拒绝了 " + displayName(applicant) + " 的入群申请");
        }
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
            // 无需审批，直接加入
            if (existing != null) {
                existing.setDeleted(0);
                memberMapper.update(existing);
            } else {
                memberMapper.insert(ImConversationMember.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .role(ImConversationMember.ROLE_MEMBER)
                        .muted(0)
                        .deleted(0)
                        .build());
            }
            SysUser user = sysUserMapper.selectOneById(userId);
            emitSystemTip(userId, conversationId, displayName(user) + " 加入了群聊");
        } else {
            // 需审批，通知群主/管理员
            SysUser user = sysUserMapper.selectOneById(userId);
            String tip = displayName(user) + " 申请加入群聊" + (message != null && !message.isBlank() ? "：" + message : "");
            emitSystemTip(userId, conversationId, tip);
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
    public long getAnnouncementReadCount(Long conversationId) {
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
