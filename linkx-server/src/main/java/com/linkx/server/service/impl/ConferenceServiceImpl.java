package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.entity.Conference;
import com.linkx.server.entity.ConferenceMember;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ConferenceMapper;
import com.linkx.server.mapper.ConferenceMemberMapper;
import com.linkx.server.service.CallService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.ConferenceService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConferenceServiceImpl implements ConferenceService {

    private static final String CALL_ID_KEY = "linkx:conference:call:";

    private final ConferenceMapper conferenceMapper;
    private final ConferenceMemberMapper memberMapper;
    private final CallService callService;
    private final ChatService chatService;
    private final ImMessagePushService pushService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public ConferenceInfoVO create(Long userId, ConferenceCreateDTO dto) {
        chatService.assertConversationMember(userId, dto.getConversationId());

        String passwordHash = null;
        if (StringUtils.hasText(dto.getPassword())) {
            passwordHash = BCrypt.hashpw(dto.getPassword().trim(), BCrypt.gensalt(12));
        }
        Conference conference = Conference.builder()
                .title(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "多人会议")
                .type(StringUtils.hasText(dto.getType()) ? dto.getType() : "video")
                .creatorId(userId)
                .conversationId(dto.getConversationId())
                .status(Conference.STATUS_ACTIVE)
                .maxParticipants(dto.getMaxParticipants() != null ? dto.getMaxParticipants() : 9)
                .password(passwordHash)
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
                .joinTime(new Date())
                .createTime(new Date())
                .build();
        memberMapper.insert(host);

        String callId = callService.createConference(
                userId, dto.getConversationId(), conference.getType(), conference.getId());
        redisTemplate.opsForValue().set(CALL_ID_KEY + conference.getId(), callId, Duration.ofHours(4));

        return toInfo(conference, callId);
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

        long activeCount = memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        if (activeCount >= conference.getMaxParticipants()) {
            throw new CustomException(400, "会议人数已满");
        }

        ConferenceMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getUserId).eq(userId)
        );
        if (existing == null) {
            memberMapper.insert(ConferenceMember.builder()
                    .conferenceId(conferenceId)
                    .userId(userId)
                    .role(ConferenceMember.ROLE_MEMBER)
                    .muted(0)
                    .videoOff(0)
                    .leftFlag(0)
                    .joinTime(new Date())
                    .createTime(new Date())
                    .build());
        } else {
            existing.setLeftFlag(0);
            existing.setLeaveTime(null);
            existing.setJoinTime(new Date());
            memberMapper.update(existing);
        }

        String callId = requireCallId(conferenceId);
        callService.joinConference(userId, callId);
        return toInfo(conference, callId);
    }

    @Override
    @Transactional
    public void leave(Long userId, Long conferenceId) {
        ConferenceMember member = requireMember(conferenceId, userId);
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
        return toInfo(conference, redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId));
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
                .map(c -> toInfo(c, redisTemplate.opsForValue().get(CALL_ID_KEY + c.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void mute(Long userId, Long conferenceId, Long targetUserId, boolean muted) {
        requireHostOrSelf(conferenceId, userId, targetUserId);
        ConferenceMember target = requireMember(conferenceId, targetUserId);
        target.setMuted(muted ? 1 : 0);
        memberMapper.update(target);
        pushService.pushToUser(targetUserId, "conference_mute", Map.of(
                "conferenceId", conferenceId,
                "muted", muted
        ));
    }

    @Override
    @Transactional
    public void setVideo(Long userId, Long conferenceId, boolean videoOff) {
        ConferenceMember member = requireMember(conferenceId, userId);
        member.setVideoOff(videoOff ? 1 : 0);
        memberMapper.update(member);
        String callId = requireCallId(conferenceId);
        callService.switchDevice(userId, callId, "video", !videoOff);
    }

    @Override
    @Transactional
    public void removeMember(Long hostId, Long conferenceId, Long targetUserId) {
        requireHost(conferenceId, hostId);
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
        memberMapper.update(oldHost);
        memberMapper.update(newHost);
        pushService.pushToUser(newHostId, "conference_host", Map.of("conferenceId", conferenceId));
    }

    @Override
    public void signal(Long userId, ConferenceSignalDTO dto) {
        requireMember(dto.getConferenceId(), userId);
        String callId = requireCallId(dto.getConferenceId());
        CallSignalDTO signal = new CallSignalDTO();
        signal.setCallId(callId);
        signal.setSignalType(dto.getSignalType());
        signal.setSdp(dto.getSdp());
        signal.setCandidate(dto.getCandidate());
        signal.setTargetUserId(dto.getTargetUserId());
        callService.signal(userId, signal);
    }

    private ConferenceInfoVO toInfo(Conference conference, String callId) {
        List<Map<String, Object>> participants = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conference.getId())
                        .and(ConferenceMember::getLeftFlag).eq(0)
        ).stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", m.getUserId());
            map.put("role", m.getRole());
            map.put("muted", Objects.equals(m.getMuted(), 1));
            map.put("videoOff", Objects.equals(m.getVideoOff(), 1));
            map.put("joinTime", m.getJoinTime());
            return map;
        }).collect(Collectors.toList());

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

    private void requireHost(Long conferenceId, Long userId) {
        ConferenceMember member = requireMember(conferenceId, userId);
        if (!ConferenceMember.ROLE_HOST.equals(member.getRole())) {
            throw new CustomException(403, "仅主持人可操作");
        }
    }

    private void requireHostOrSelf(Long conferenceId, Long operatorId, Long targetUserId) {
        if (Objects.equals(operatorId, targetUserId)) {
            requireMember(conferenceId, operatorId);
            return;
        }
        requireHost(conferenceId, operatorId);
    }

    private String requireCallId(Long conferenceId) {
        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        if (callId == null || callId.isBlank()) {
            throw new CustomException(404, "会议信令通道不存在或已过期");
        }
        return callId;
    }
}
