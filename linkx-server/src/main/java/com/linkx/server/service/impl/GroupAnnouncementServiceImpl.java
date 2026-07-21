package com.linkx.server.service.impl;

import com.linkx.server.common.InputSanitizer;
import com.linkx.server.controller.dto.CreateGroupAnnouncementDTO;
import com.linkx.server.controller.dto.UpdateGroupAnnouncementDTO;
import com.linkx.server.controller.vo.GroupAnnouncementVO;
import com.linkx.server.entity.GroupAnnouncement;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.GroupAnnouncementMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.GroupAnnouncementService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAnnouncementServiceImpl implements GroupAnnouncementService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GroupAnnouncementMapper announcementMapper;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<GroupAnnouncementVO> list(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);
        migrateLegacyIfNeeded(conversationId);
        List<GroupAnnouncement> list = announcementMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupAnnouncement::getConversationId).eq(conversationId)
                        .orderBy(GroupAnnouncement::getPinned, false)
                        .orderBy(GroupAnnouncement::getCreateTime, false)
        );
        return toVOList(list, conversationId);
    }

    @Override
    public GroupAnnouncementVO display(Long userId, Long conversationId) {
        assertGroupMember(userId, conversationId);
        migrateLegacyIfNeeded(conversationId);
        GroupAnnouncement picked = pickDisplay(conversationId);
        if (picked == null) {
            return null;
        }
        Map<Long, SysUser> users = loadUsers(Set.of(picked.getPublisherId()));
        Map<Long, String> roles = loadRoles(conversationId, Set.of(picked.getPublisherId()));
        return toVO(picked, users.get(picked.getPublisherId()), roles.get(picked.getPublisherId()));
    }

    @Override
    @Transactional
    public GroupAnnouncementVO create(Long userId, Long conversationId, CreateGroupAnnouncementDTO dto) {
        assertGroupAdmin(userId, conversationId);
        String content = InputSanitizer.sanitizeText(dto.getContent().trim(), 5000);
        if (!StringUtils.hasText(content)) {
            throw new CustomException(400, "公告内容不能为空");
        }
        boolean pin = Boolean.TRUE.equals(dto.getPinned());
        if (pin) {
            clearPinned(conversationId);
        }
        GroupAnnouncement row = GroupAnnouncement.builder()
                .conversationId(conversationId)
                .content(content)
                .publisherId(userId)
                .pinned(pin ? 1 : 0)
                .build();
        announcementMapper.insert(row);
        syncConversationSummary(conversationId);
        SysUser publisher = sysUserMapper.selectOneById(userId);
        String role = resolveRole(conversationId, userId);
        return toVO(row, publisher, role);
    }

    @Override
    @Transactional
    public GroupAnnouncementVO update(
            Long userId, Long conversationId, Long announcementId, UpdateGroupAnnouncementDTO dto) {
        assertGroupAdmin(userId, conversationId);
        GroupAnnouncement row = requireAnnouncement(conversationId, announcementId);
        if (dto.getContent() != null) {
            String content = InputSanitizer.sanitizeText(dto.getContent().trim(), 5000);
            if (!StringUtils.hasText(content)) {
                throw new CustomException(400, "公告内容不能为空");
            }
            row.setContent(content);
        }
        if (dto.getPinned() != null) {
            if (Boolean.TRUE.equals(dto.getPinned())) {
                clearPinned(conversationId);
                row.setPinned(1);
            } else {
                row.setPinned(0);
            }
        }
        row.setUpdateTime(new Date());
        announcementMapper.update(row);
        syncConversationSummary(conversationId);
        SysUser publisher = sysUserMapper.selectOneById(row.getPublisherId());
        String role = resolveRole(conversationId, row.getPublisherId());
        return toVO(row, publisher, role);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long conversationId, Long announcementId) {
        assertGroupAdmin(userId, conversationId);
        GroupAnnouncement row = requireAnnouncement(conversationId, announcementId);
        announcementMapper.deleteById(row.getId());
        syncConversationSummary(conversationId);
    }

    /** 把旧版 im_conversation.announcement 迁移为一条公告记录（仅一次） */
    private void migrateLegacyIfNeeded(Long conversationId) {
        long count = announcementMapper.selectCountByQuery(
                QueryWrapper.create().where(GroupAnnouncement::getConversationId).eq(conversationId)
        );
        if (count > 0) {
            return;
        }
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || !StringUtils.hasText(group.getAnnouncement())) {
            return;
        }
        Long publisherId = group.getOwnerId();
        GroupAnnouncement row = GroupAnnouncement.builder()
                .conversationId(conversationId)
                .content(group.getAnnouncement().trim())
                .publisherId(publisherId)
                .pinned(1)
                .build();
        announcementMapper.insert(row);
    }

    /** 同步会话表摘要字段，供会话列表兼容展示 */
    private void syncConversationSummary(Long conversationId) {
        GroupAnnouncement picked = pickDisplay(conversationId);
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null) {
            return;
        }
        group.setAnnouncement(picked == null ? "" : picked.getContent());
        conversationMapper.update(group);
    }

    private GroupAnnouncement pickDisplay(Long conversationId) {
        List<GroupAnnouncement> list = announcementMapper.selectListByQuery(
                QueryWrapper.create().where(GroupAnnouncement::getConversationId).eq(conversationId)
        );
        if (list.isEmpty()) {
            return null;
        }
        return list.stream()
                .filter(a -> a.getPinned() != null && a.getPinned() == 1)
                .max(Comparator.comparing(GroupAnnouncement::getUpdateTime, Comparator.nullsLast(Date::compareTo))
                        .thenComparing(GroupAnnouncement::getCreateTime, Comparator.nullsLast(Date::compareTo)))
                .orElseGet(() -> list.stream()
                        .max(Comparator.comparing(GroupAnnouncement::getCreateTime, Comparator.nullsLast(Date::compareTo)))
                        .orElse(null));
    }

    private void clearPinned(Long conversationId) {
        List<GroupAnnouncement> pinned = announcementMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupAnnouncement::getConversationId).eq(conversationId)
                        .and(GroupAnnouncement::getPinned).eq(1)
        );
        for (GroupAnnouncement a : pinned) {
            a.setPinned(0);
            announcementMapper.update(a);
        }
    }

    private GroupAnnouncement requireAnnouncement(Long conversationId, Long announcementId) {
        GroupAnnouncement row = announcementMapper.selectOneById(announcementId);
        if (row == null || !Objects.equals(row.getConversationId(), conversationId)) {
            throw new CustomException(404, "公告不存在");
        }
        return row;
    }

    private void assertGroupMember(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "你不是该群成员");
        }
    }

    private void assertGroupAdmin(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        if (Objects.equals(group.getOwnerId(), userId)) {
            return;
        }
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null || !ImConversationMember.ROLE_ADMIN.equals(member.getRole())) {
            throw new CustomException(403, "只有群主或管理员才能管理公告");
        }
    }

    private String resolveRole(Long conversationId, Long userId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group != null && Objects.equals(group.getOwnerId(), userId)) {
            return ImConversationMember.ROLE_OWNER;
        }
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        return member != null && StringUtils.hasText(member.getRole())
                ? member.getRole()
                : ImConversationMember.ROLE_MEMBER;
    }

    private List<GroupAnnouncementVO> toVOList(List<GroupAnnouncement> list, Long conversationId) {
        Set<Long> ids = list.stream().map(GroupAnnouncement::getPublisherId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(ids);
        Map<Long, String> roles = loadRoles(conversationId, ids);
        return list.stream()
                .map(a -> toVO(a, users.get(a.getPublisherId()), roles.get(a.getPublisherId())))
                .collect(Collectors.toList());
    }

    private Map<Long, String> loadRoles(Long conversationId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        ImConversation group = conversationMapper.selectOneById(conversationId);
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).in(userIds)
        );
        Map<Long, String> map = members.stream()
                .collect(Collectors.toMap(ImConversationMember::getUserId, ImConversationMember::getRole, (a, b) -> a));
        if (group != null && group.getOwnerId() != null) {
            map.put(group.getOwnerId(), ImConversationMember.ROLE_OWNER);
        }
        return map;
    }

    private Map<Long, SysUser> loadUsers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(ids))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private GroupAnnouncementVO toVO(GroupAnnouncement row, SysUser publisher, String role) {
        return GroupAnnouncementVO.builder()
                .id(row.getId())
                .conversationId(row.getConversationId())
                .content(row.getContent())
                .publisherId(row.getPublisherId())
                .publisherNickname(publisher != null ? publisher.getNickname() : null)
                .publisherRole(role)
                .pinned(row.getPinned() != null && row.getPinned() == 1)
                .createTime(row.getCreateTime() == null ? null : TIME_FMT.format(row.getCreateTime().toInstant()))
                .updateTime(row.getUpdateTime() == null ? null : TIME_FMT.format(row.getUpdateTime().toInstant()))
                .build();
    }
}
