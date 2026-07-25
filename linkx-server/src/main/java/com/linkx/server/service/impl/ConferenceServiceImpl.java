package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.entity.Conference;
import com.linkx.server.entity.ConferenceMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ConferenceMapper;
import com.linkx.server.mapper.ConferenceMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.CallService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.ConferenceService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConferenceServiceImpl implements ConferenceService {

    private static final String CALL_ID_KEY = "linkx:conference:call:";

    private final ConferenceMapper conferenceMapper;
    private final ConferenceMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final MediaUrlService mediaUrlService;
    private final CallService callService;
    private final ChatService chatService;
    private final ImMessagePushService pushService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public ConferenceInfoVO create(Long userId, ConferenceCreateDTO dto) {
        chatService.assertConversationMember(userId, dto.getConversationId());

        // 同会话已有 ACTIVE：复用并入会（避免多房并存）
        Conference existing = conferenceMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(Conference::getConversationId).eq(dto.getConversationId())
                        .and(Conference::getStatus).eq(Conference.STATUS_ACTIVE)
                        .limit(1)
        );
        if (existing != null) {
            ConferenceInfoVO vo = join(userId, existing.getId(), dto.getPassword());
            vo.setReused(true);
            return vo;
        }

        String passwordHash = null;
        if (StringUtils.hasText(dto.getPassword())) {
            passwordHash = BCrypt.hashpw(dto.getPassword().trim(), BCrypt.gensalt(12));
        }
        int max = dto.getMaxParticipants() != null ? dto.getMaxParticipants() : 9;
        if (max < 2) max = 2;
        if (max > 16) max = 16;
        Conference conference = Conference.builder()
                .title(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "多人会议")
                .type(StringUtils.hasText(dto.getType()) ? dto.getType() : "video")
                .creatorId(userId)
                .conversationId(dto.getConversationId())
                .status(Conference.STATUS_ACTIVE)
                .maxParticipants(max)
                .password(passwordHash)
                .lobbyEnabled(Boolean.TRUE.equals(dto.getLobbyEnabled()) ? 1 : 0)
                .startTime(new Date())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        conferenceMapper.insert(conference);

        ConferenceMember host = ConferenceMember.builder()
                .conferenceId(conference.getId())
                .userId(userId)
                .role(ConferenceMember.ROLE_HOST)
                .muted(0)
                .videoOff(0)
                .leftFlag(0)
                .admitStatus(1)
                .joinTime(new Date())
                .createTime(new Date())
                .build();
        memberMapper.insert(host);

        String callId = callService.createConference(
                userId,
                dto.getConversationId(),
                conference.getType(),
                conference.getId(),
                conference.getTitle(),
                StringUtils.hasText(passwordHash));
        redisTemplate.opsForValue().set(CALL_ID_KEY + conference.getId(), callId, Duration.ofHours(4));

        ConferenceInfoVO vo = toInfo(conference, callId, userId);
        vo.setReused(false);
        return vo;
    }

    @Override
    @Transactional
    public ConferenceInfoVO join(Long userId, Long conferenceId, String password) {
        Conference conference = requireActive(conferenceId);
        // 必须是会话成员，防止猜 ID 入会绕过群/私聊 ACL
        chatService.assertConversationMember(userId, conference.getConversationId());
        if (StringUtils.hasText(conference.getPassword())) {
            String input = password != null ? password.trim() : "";
            String stored = conference.getPassword();
            boolean ok;
            if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
                ok = StringUtils.hasText(input) && BCrypt.checkpw(input, stored);
            } else {
                // 兼容历史明文会议口令（一次性比对后仍不回写，新会议一律哈希）
                ok = Objects.equals(stored, input);
            }
            if (!ok) {
                throw new CustomException(403, "会议密码错误");
            }
        }

        boolean lobbyOn = Objects.equals(conference.getLobbyEnabled(), 1);
        boolean isCreator = Objects.equals(conference.getCreatorId(), userId);

        long activeCount = memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
                        .and(ConferenceMember::getAdmitStatus).eq(1)
        );

        ConferenceMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getUserId).eq(userId)
        );

        boolean admitNow = !lobbyOn || isCreator
                || (existing != null && ConferenceMember.ROLE_HOST.equals(existing.getRole()))
                || (existing != null && ConferenceMember.ROLE_CO_HOST.equals(existing.getRole()))
                || (existing != null && Objects.equals(existing.getAdmitStatus(), 1) && Objects.equals(existing.getLeftFlag(), 0));

        if (admitNow && existing == null && activeCount >= conference.getMaxParticipants()) {
            throw new CustomException(400, "会议人数已满（上限 " + conference.getMaxParticipants() + " 人，mesh 建议≤9）");
        }
        if (admitNow && existing != null && Objects.equals(existing.getLeftFlag(), 1)
                && activeCount >= conference.getMaxParticipants()) {
            throw new CustomException(400, "会议人数已满（上限 " + conference.getMaxParticipants() + " 人，mesh 建议≤9）");
        }

        if (existing == null) {
            memberMapper.insert(ConferenceMember.builder()
                    .conferenceId(conferenceId)
                    .userId(userId)
                    .role(ConferenceMember.ROLE_MEMBER)
                    .muted(0)
                    .videoOff(0)
                    .leftFlag(0)
                    .admitStatus(admitNow ? 1 : 0)
                    .joinTime(new Date())
                    .createTime(new Date())
                    .build());
        } else {
            existing.setLeftFlag(0);
            existing.setLeaveTime(null);
            existing.setJoinTime(new Date());
            if (admitNow) {
                existing.setAdmitStatus(1);
            } else if (!Objects.equals(existing.getAdmitStatus(), 1)) {
                existing.setAdmitStatus(0);
            }
            memberMapper.update(existing);
        }

        String callId = requireCallId(conferenceId);
        if (admitNow) {
            callService.joinConference(userId, callId);
            broadcastToActiveMembers(conferenceId, "conference_join", Map.of(
                    "conferenceId", conferenceId,
                    "userId", userId,
                    "callId", callId
            ));
        } else {
            // 通知主持人/联席：有人在等候室
            broadcastToHosts(conferenceId, "conference_waiting", Map.of(
                    "conferenceId", conferenceId,
                    "userId", userId
            ));
        }
        return toInfo(conference, callId, userId);
    }

    @Override
    @Transactional
    public void leave(Long userId, Long conferenceId) {
        ConferenceMember member = requireMember(conferenceId, userId);
        boolean wasHost = ConferenceMember.ROLE_HOST.equals(member.getRole());

        if (wasHost) {
            List<ConferenceMember> others = memberMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ConferenceMember::getConferenceId).eq(conferenceId)
                            .and(ConferenceMember::getLeftFlag).eq(0)
                            .and(ConferenceMember::getUserId).ne(userId)
            );
            if (others.isEmpty()) {
                // 主持人是最后一人：直接结束，避免僵尸 ACTIVE
                end(userId, conferenceId);
                return;
            }
            ConferenceMember next = others.stream()
                    .min((a, b) -> {
                        Date ta = a.getJoinTime() != null ? a.getJoinTime() : a.getCreateTime();
                        Date tb = b.getJoinTime() != null ? b.getJoinTime() : b.getCreateTime();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return ta.compareTo(tb);
                    })
                    .orElse(others.get(0));
            transferHost(userId, conferenceId, next.getUserId());
            // transfer 后本端仍是成员，继续走离开
            member = requireMember(conferenceId, userId);
        }

        member.setLeftFlag(1);
        member.setLeaveTime(new Date());
        memberMapper.update(member);

        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        if (callId != null) {
            callService.leaveConference(userId, callId);
        }
    }

    @Override
    @Transactional
    public void end(Long userId, Long conferenceId) {
        Conference conference = requireActive(conferenceId);
        ConferenceMember host = requireMember(conferenceId, userId);
        if (!ConferenceMember.ROLE_HOST.equals(host.getRole())
                && !conference.getCreatorId().equals(userId)) {
            throw new CustomException(403, "仅主持人可结束会议");
        }
        conference.setStatus(Conference.STATUS_ENDED);
        conference.setEndTime(new Date());
        conference.setUpdateTime(new Date());
        conferenceMapper.update(conference);

        List<ConferenceMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        for (ConferenceMember m : members) {
            m.setLeftFlag(1);
            m.setLeaveTime(new Date());
            memberMapper.update(m);
            if (callId != null) {
                callService.leaveConference(m.getUserId(), callId);
            }
            pushService.pushToUser(m.getUserId(), "conference_end", Map.of(
                    "conferenceId", conferenceId,
                    "callId", callId != null ? callId : ""
            ));
        }
        redisTemplate.delete(CALL_ID_KEY + conferenceId);
    }

    @Override
    public ConferenceInfoVO info(Long userId, Long conferenceId) {
        Conference conference = conferenceMapper.selectOneById(conferenceId);
        if (conference == null) {
            throw new CustomException(404, "会议不存在");
        }
        chatService.assertConversationMember(userId, conference.getConversationId());
        return toInfo(conference, redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId), userId);
    }

    @Override
    public List<ConferenceInfoVO> listActive(Long userId) {
        List<ConferenceMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getUserId).eq(userId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        return memberships.stream()
                .map(m -> conferenceMapper.selectOneById(m.getConferenceId()))
                .filter(c -> c != null && Objects.equals(c.getStatus(), Conference.STATUS_ACTIVE))
                .map(c -> toInfo(c, redisTemplate.opsForValue().get(CALL_ID_KEY + c.getId()), userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void mute(Long userId, Long conferenceId, Long targetUserId, boolean muted) {
        requireHostCoHostOrSelf(conferenceId, userId, targetUserId);
        ConferenceMember target = requireMember(conferenceId, targetUserId);
        target.setMuted(muted ? 1 : 0);
        memberMapper.update(target);
        // 广播给会中所有人，方便各端实时刷新静音图标
        broadcastToActiveMembers(conferenceId, "conference_mute", Map.of(
                "conferenceId", conferenceId,
                "userId", targetUserId,
                "muted", muted
        ));
    }

    @Override
    @Transactional
    public void setVideo(Long userId, Long conferenceId, boolean videoOff) {
        // 多人会议只维护 conference_member.video_off 并广播，勿走 1v1 CallService.switchDevice：
        // 会议 call 无 caller/callee 哈希，switchDevice 会抛错并回滚本事务，导致图标与广播都不更新。
        ConferenceMember member = requireMember(conferenceId, userId);
        member.setVideoOff(videoOff ? 1 : 0);
        memberMapper.update(member);
        broadcastToActiveMembers(conferenceId, "conference_video", Map.of(
                "conferenceId", conferenceId,
                "userId", userId,
                "videoOff", videoOff
        ));
    }

    /** 向当前仍在会中且已准入的成员推送同一事件 */
    private void broadcastToActiveMembers(Long conferenceId, String action, Map<String, Object> payload) {
        List<ConferenceMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
                        .and(ConferenceMember::getAdmitStatus).eq(1)
        );
        for (ConferenceMember m : members) {
            pushService.pushToUser(m.getUserId(), action, payload);
        }
    }

    @Override
    @Transactional
    public void removeMember(Long hostId, Long conferenceId, Long targetUserId) {
        requireHostOrCoHost(conferenceId, hostId);
        leave(targetUserId, conferenceId);
        pushService.pushToUser(targetUserId, "conference_remove", Map.of("conferenceId", conferenceId));
    }

    @Override
    @Transactional
    public void transferHost(Long hostId, Long conferenceId, Long newHostId) {
        requireHost(conferenceId, hostId);
        ConferenceMember oldHost = requireMember(conferenceId, hostId);
        ConferenceMember newHost = requireMember(conferenceId, newHostId);
        oldHost.setRole(ConferenceMember.ROLE_MEMBER);
        newHost.setRole(ConferenceMember.ROLE_HOST);
        newHost.setAdmitStatus(1);
        memberMapper.update(oldHost);
        memberMapper.update(newHost);
        broadcastToActiveMembers(conferenceId, "conference_host", Map.of(
                "conferenceId", conferenceId,
                "previousHostId", hostId,
                "newHostId", newHostId
        ));
    }

    @Override
    @Transactional
    public void admitMember(Long hostId, Long conferenceId, Long targetUserId) {
        requireHostOrCoHost(conferenceId, hostId);
        Conference conference = requireActive(conferenceId);
        ConferenceMember target = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getUserId).eq(targetUserId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        if (target == null) {
            throw new CustomException(404, "目标用户不在等候室");
        }
        if (Objects.equals(target.getAdmitStatus(), 1)) {
            return;
        }
        long activeCount = memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
                        .and(ConferenceMember::getAdmitStatus).eq(1)
        );
        if (activeCount >= conference.getMaxParticipants()) {
            throw new CustomException(400, "会议人数已满（上限 " + conference.getMaxParticipants() + " 人）");
        }
        target.setAdmitStatus(1);
        memberMapper.update(target);
        String callId = requireCallId(conferenceId);
        callService.joinConference(targetUserId, callId);
        broadcastToActiveMembers(conferenceId, "conference_admit", Map.of(
                "conferenceId", conferenceId,
                "userId", targetUserId,
                "callId", callId
        ));
    }

    @Override
    @Transactional
    public void setMemberRole(Long hostId, Long conferenceId, Long targetUserId, String role) {
        requireHost(conferenceId, hostId);
        if (Objects.equals(hostId, targetUserId)) {
            throw new CustomException(400, "不能修改自己的角色，请使用转让主持人");
        }
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!ConferenceMember.ROLE_CO_HOST.equals(normalized)
                && !ConferenceMember.ROLE_MEMBER.equals(normalized)) {
            throw new CustomException(400, "仅支持设为联席主持人或普通成员");
        }
        ConferenceMember target = requireMember(conferenceId, targetUserId);
        if (ConferenceMember.ROLE_HOST.equals(target.getRole())) {
            throw new CustomException(400, "请先转让主持人");
        }
        target.setRole(normalized);
        target.setAdmitStatus(1);
        memberMapper.update(target);
        broadcastToActiveMembers(conferenceId, "conference_role", Map.of(
                "conferenceId", conferenceId,
                "userId", targetUserId,
                "role", normalized
        ));
    }

    @Override
    public void raiseHand(Long userId, Long conferenceId, boolean raised) {
        requireAdmittedMember(conferenceId, userId);
        broadcastToActiveMembers(conferenceId, "conference_raise", Map.of(
                "conferenceId", conferenceId,
                "userId", userId,
                "raised", raised
        ));
    }

    @Override
    public List<ConferenceInfoVO> listHistory(Long userId, Long conversationId) {
        chatService.assertConversationMember(userId, conversationId);
        List<Conference> list = conferenceMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Conference::getConversationId).eq(conversationId)
                        .and(Conference::getStatus).eq(Conference.STATUS_ENDED)
                        .orderBy(Conference::getEndTime, false)
                        .limit(50)
        );
        return list.stream()
                .map(c -> toInfo(c, null, userId))
                .collect(Collectors.toList());
    }

    @Override
    public void signal(Long userId, ConferenceSignalDTO dto) {
        requireAdmittedMember(dto.getConferenceId(), userId);
        String callId = requireCallId(dto.getConferenceId());
        // 长会期间信令续期，避免 CALL_ID / call hash 提前过期
        redisTemplate.expire(CALL_ID_KEY + dto.getConferenceId(), Duration.ofHours(4));
        CallSignalDTO signal = new CallSignalDTO();
        signal.setCallId(callId);
        signal.setSignalType(dto.getSignalType());
        signal.setSdp(dto.getSdp());
        signal.setCandidate(dto.getCandidate());
        signal.setTargetUserId(dto.getTargetUserId());
        callService.signal(userId, signal);
    }

    private ConferenceInfoVO toInfo(Conference conference, String callId) {
        return toInfo(conference, callId, null);
    }

    private ConferenceInfoVO toInfo(Conference conference, String callId, Long viewerId) {
        List<ConferenceMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conference.getId())
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        Set<Long> userIds = members.stream()
                .map(ConferenceMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectListByQuery(
                        QueryWrapper.create().where(SysUser::getId).in(userIds)
                ).stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> participants = members.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", m.getUserId());
            map.put("role", m.getRole());
            map.put("muted", Objects.equals(m.getMuted(), 1));
            map.put("videoOff", Objects.equals(m.getVideoOff(), 1));
            map.put("admitStatus", m.getAdmitStatus() == null ? 1 : m.getAdmitStatus());
            map.put("joinTime", m.getJoinTime());
            SysUser user = userMap.get(m.getUserId());
            if (user != null) {
                String nick = StringUtils.hasText(user.getNickname())
                        ? user.getNickname()
                        : (StringUtils.hasText(user.getUsername()) ? user.getUsername() : "用户");
                map.put("nickname", nick);
                map.put("avatar", mediaUrlService.resolve(user.getAvatar()));
            } else {
                map.put("nickname", "用户");
                map.put("avatar", null);
            }
            return map;
        }).collect(Collectors.toList());

        boolean waiting = false;
        if (viewerId != null) {
            waiting = members.stream()
                    .anyMatch(m -> Objects.equals(m.getUserId(), viewerId)
                            && Objects.equals(m.getAdmitStatus(), 0));
        }

        return ConferenceInfoVO.builder()
                .id(conference.getId())
                .title(conference.getTitle())
                .type(conference.getType())
                .creatorId(conference.getCreatorId())
                .conversationId(conference.getConversationId())
                .status(conference.getStatus())
                .maxParticipants(conference.getMaxParticipants())
                .startTime(conference.getStartTime())
                .endTime(conference.getEndTime())
                .callId(callId)
                .hasPassword(StringUtils.hasText(conference.getPassword()))
                .lobbyEnabled(Objects.equals(conference.getLobbyEnabled(), 1))
                .waitingAdmit(waiting)
                .participants(participants)
                .build();
    }

    private Conference requireActive(Long conferenceId) {
        Conference conference = conferenceMapper.selectOneById(conferenceId);
        if (conference == null) {
            throw new CustomException(404, "会议不存在");
        }
        if (!Objects.equals(conference.getStatus(), Conference.STATUS_ACTIVE)) {
            throw new CustomException(400, "会议已结束");
        }
        return conference;
    }

    private ConferenceMember requireMember(Long conferenceId, Long userId) {
        ConferenceMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getUserId).eq(userId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        if (member == null) {
            throw new CustomException(403, "你不在该会议中");
        }
        return member;
    }

    private ConferenceMember requireAdmittedMember(Long conferenceId, Long userId) {
        ConferenceMember member = requireMember(conferenceId, userId);
        if (!Objects.equals(member.getAdmitStatus(), 1)) {
            throw new CustomException(403, "仍在等候室，请等待主持人准入");
        }
        return member;
    }

    private void requireHost(Long conferenceId, Long userId) {
        ConferenceMember member = requireMember(conferenceId, userId);
        if (!ConferenceMember.ROLE_HOST.equals(member.getRole())) {
            throw new CustomException(403, "仅主持人可操作");
        }
    }

    private void requireHostOrCoHost(Long conferenceId, Long userId) {
        ConferenceMember member = requireMember(conferenceId, userId);
        if (!ConferenceMember.ROLE_HOST.equals(member.getRole())
                && !ConferenceMember.ROLE_CO_HOST.equals(member.getRole())) {
            throw new CustomException(403, "仅主持人或联席主持人可操作");
        }
    }

    private void requireHostCoHostOrSelf(Long conferenceId, Long operatorId, Long targetUserId) {
        if (Objects.equals(operatorId, targetUserId)) {
            requireMember(conferenceId, operatorId);
            return;
        }
        requireHostOrCoHost(conferenceId, operatorId);
    }

    private void broadcastToHosts(Long conferenceId, String action, Map<String, Object> payload) {
        List<ConferenceMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
                        .and(ConferenceMember::getAdmitStatus).eq(1)
        );
        for (ConferenceMember m : members) {
            if (ConferenceMember.ROLE_HOST.equals(m.getRole())
                    || ConferenceMember.ROLE_CO_HOST.equals(m.getRole())) {
                pushService.pushToUser(m.getUserId(), action, payload);
            }
        }
    }

    private String requireCallId(Long conferenceId) {
        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        if (callId == null || callId.isBlank()) {
            throw new CustomException(404, "会议信令通道不存在或已过期");
        }
        return callId;
    }
}
