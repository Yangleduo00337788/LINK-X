package com.linkx.server.service.impl;

import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.controller.vo.MessageVO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
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
@Slf4j
public class ConferenceServiceImpl implements ConferenceService {

    private static final String CALL_ID_KEY = "linkx:conference:call:";
    /** 会议活跃已准入成员计数 key 前缀（Redis 原子计数器，防止 join 的 check-then-act 竞态导致超 maxParticipants） */
    private static final String ACTIVE_COUNT_KEY = "linkx:conference:active_count:";
    /** 计数 key 的保护性 TTL，避免极端场景（如 end 未触发）计数键长期残留 */
    private static final Duration ACTIVE_COUNT_TTL = Duration.ofHours(8);

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

        // 同会话已有 ACTIVE：复用；若信令通道已失效则重建，避免「会议不存在或已过期」
        List<Conference> actives = conferenceMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Conference::getConversationId).eq(dto.getConversationId())
                        .and(Conference::getStatus).eq(Conference.STATUS_ACTIVE)
                        .orderBy(Conference::getStartTime, false)
        );
        if (!actives.isEmpty()) {
            // 只保留最新一场，其余僵尸 ACTIVE 收口结束
            Conference keep = actives.get(0);
            for (int i = 1; i < actives.size(); i++) {
                forceEndConference(actives.get(i));
            }
            ensureCallChannel(keep, userId);
            ConferenceInfoVO vo = join(userId, keep.getId(), dto.getPassword());
            vo.setReused(true);
            return vo;
        }

        String passwordHash = null;
        if (StringUtils.hasText(dto.getPassword())) {
            passwordHash = PasswordEncoderHolder.encode(dto.getPassword().trim());
        }
        int max = dto.getMaxParticipants() != null ? dto.getMaxParticipants() : 9;
        if (max < 2) max = 2;
        if (max > 16) max = 16;
        String scene = Conference.SCENE_CALL.equalsIgnoreCase(dto.getScene())
                ? Conference.SCENE_CALL
                : Conference.SCENE_MEETING;
        String mediaType = StringUtils.hasText(dto.getType()) ? dto.getType() : "video";
        String defaultTitle = Conference.SCENE_CALL.equals(scene)
                ? ("voice".equalsIgnoreCase(mediaType) ? "语音通话" : "视频通话")
                : "多人会议";
        Conference conference = Conference.builder()
                .title(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : defaultTitle)
                .type(mediaType)
                .scene(scene)
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

        // 初始化活跃已准入成员计数（主持人 1 人）；放到 afterCommit 避免 DB 回滚后 Redis 残留
        final Long createdConferenceId = conference.getId();
        runAfterCommit(() -> {
            try {
                redisTemplate.opsForValue().set(
                        ACTIVE_COUNT_KEY + createdConferenceId, "1", ACTIVE_COUNT_TTL);
            } catch (Exception e) {
                log.warn("init conference active count failed, conferenceId={}: {}",
                        createdConferenceId, e.toString(), e);
            }
        });

        String callId = callService.createConference(
                userId,
                dto.getConversationId(),
                conference.getType(),
                conference.getId(),
                conference.getTitle(),
                StringUtils.hasText(passwordHash),
                conference.getScene());
        // Redis 写操作放到 afterCommit，避免 DB 回滚后 CALL_ID_KEY 残留
        runAfterCommit(() -> {
            try {
                redisTemplate.opsForValue().set(
                        CALL_ID_KEY + conference.getId(), callId, Duration.ofHours(4));
            } catch (Exception e) {
                log.warn("set CALL_ID_KEY after commit failed, conferenceId={}: {}",
                        conference.getId(), e.toString(), e);
            }
        });

        emitConferenceInviteMessage(
                userId,
                dto.getConversationId(),
                conference.getId(),
                conference.getTitle(),
                conference.getType(),
                conference.getScene(),
                StringUtils.hasText(passwordHash));

        ConferenceInfoVO vo = toInfo(conference, callId, userId);
        vo.setReused(false);
        notifyConversationPresence(conference);
        return vo;
    }

    /** 会话时间线写入邀请提示，并推送给成员（含发起人，便于本端即时展示） */
    private void emitConferenceInviteMessage(
            Long senderId,
            Long conversationId,
            Long conferenceId,
            String title,
            String callType,
            String scene,
            boolean hasPassword) {
        Runnable task = () -> {
            try {
                MessageVO tip = chatService.postConferenceInviteMessage(
                        senderId, conversationId, conferenceId, title, callType, scene, hasPassword);
                pushService.pushToConversationMembers(tip, senderId, null);
                // 发起人不会走 pushToConversationMembers 的接收者列表，单独推一条便于本端时间线刷新
                MessageVO selfView = tip;
                selfView.setIsSelf(true);
                pushService.pushToUser(senderId, "message", selfView);
            } catch (Exception e) {
                log.warn("emitConferenceInviteMessage failed: {}", e.toString(), e);
            }
        };
        runAfterCommit(task);
    }

    /** 会话时间线写入结束提示（语音/视频通话、会议） */
    private void emitConferenceEndedMessage(Long operatorId, Conference conference) {
        if (conference == null || conference.getConversationId() == null) {
            return;
        }
        final Long conversationId = conference.getConversationId();
        final Long opId = operatorId;
        try {
            String kindLabel = kindLabelOf(conference);
            String title = StringUtils.hasText(conference.getTitle()) ? conference.getTitle().trim() : kindLabel;
            String text;
            if (opId != null) {
                SysUser op = sysUserMapper.selectOneById(opId);
                String name = op != null
                        ? (StringUtils.hasText(op.getNickname())
                            ? op.getNickname()
                            : (StringUtils.hasText(op.getUsername()) ? op.getUsername() : "用户"))
                        : "用户";
                text = name + "结束了" + kindLabel + "「" + title + "」";
            } else {
                text = kindLabel + "「" + title + "」已结束";
            }
            // 与 end 同一事务落库，避免 afterCommit 异常导致库中无结束提示
            MessageVO tip = chatService.postSystemMessage(opId, conversationId, text);
            runAfterCommit(() -> {
                try {
                    // senderId 可为 null：推给会话全部在线成员
                    pushService.pushToConversationMembers(tip, opId, null);
                    if (opId != null) {
                        MessageVO selfView = tip;
                        selfView.setIsSelf(true);
                        pushService.pushToUser(opId, "message", selfView);
                    }
                } catch (Exception e) {
                    log.warn("push conference ended tip failed: {}", e.toString(), e);
                }
            });
        } catch (Exception e) {
            log.warn("emitConferenceEndedMessage failed: {}", e.toString(), e);
        }
    }

    private static String kindLabelOf(Conference conference) {
        boolean isCall = Conference.SCENE_CALL.equalsIgnoreCase(conference.getScene());
        boolean video = !"voice".equalsIgnoreCase(conference.getType());
        if (isCall) {
            return video ? "视频通话" : "语音通话";
        }
        return "会议";
    }

    private void runAfterCommit(Runnable task) {
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
                ok = StringUtils.hasText(input) && PasswordEncoderHolder.matches(input, stored);
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

        ConferenceMember existing = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getUserId).eq(userId)
        );

        boolean admitNow = !lobbyOn || isCreator
                || (existing != null && ConferenceMember.ROLE_HOST.equals(existing.getRole()))
                || (existing != null && ConferenceMember.ROLE_CO_HOST.equals(existing.getRole()))
                || (existing != null && Objects.equals(existing.getAdmitStatus(), 1) && Objects.equals(existing.getLeftFlag(), 0));

        // 用 Redis 原子计数器替代 DB selectCount + insert 的 check-then-act 竞态，防止并发入会超 maxParticipants。
        // 仅当本次会真正新增一个活跃已准入成员时才 INCR：新成员入会 或 已离开成员重新入会。
        boolean needsIncr = admitNow && (existing == null || Objects.equals(existing.getLeftFlag(), 1));
        if (needsIncr) {
            String countKey = ACTIVE_COUNT_KEY + conferenceId;
            Long newCount = redisTemplate.opsForValue().increment(countKey);
            if (newCount != null && newCount > conference.getMaxParticipants()) {
                // 超限：原子回滚计数，保证计数与 DB 一致
                redisTemplate.opsForValue().decrement(countKey);
                throw new CustomException(400, "会议人数已满（上限 " + conference.getMaxParticipants() + " 人，mesh 建议≤9）");
            }
            if (newCount != null) {
                // 保护性 TTL，避免极端场景（如 end 未触发）计数键残留
                redisTemplate.expire(countKey, ACTIVE_COUNT_TTL);
            }
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

        String callId = ensureAndGetCallId(conference, userId);
        if (admitNow) {
            callService.joinConference(userId, callId);
            Map<String, Object> joinPayload = new HashMap<>();
            joinPayload.put("conferenceId", conferenceId);
            joinPayload.put("conversationId", conference.getConversationId());
            joinPayload.put("userId", userId);
            joinPayload.put("callId", callId != null ? callId : "");
            joinPayload.put("participantCount", countAdmitted(conferenceId));
            broadcastToActiveMembers(conferenceId, "conference_join", joinPayload);
            notifyConversationPresence(conference);
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
        Conference conference = conferenceMapper.selectOneById(conferenceId);

        if (wasHost) {
            List<ConferenceMember> others = memberMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ConferenceMember::getConferenceId).eq(conferenceId)
                            .and(ConferenceMember::getLeftFlag).eq(0)
                            .and(ConferenceMember::getUserId).ne(userId)
            );
            // 还有其他人：转让主持后自己离开；最后一人离开也不自动「结束」，
            // 保留 ACTIVE 方便会话顶栏继续展示、其他人可加入（真正散会走 end）
            if (!others.isEmpty()) {
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
                member = requireMember(conferenceId, userId);
            }
        }

        member.setLeftFlag(1);
        member.setLeaveTime(new Date());
        memberMapper.update(member);

        // 已准入成员离开时递减活跃计数，保持 Redis 计数与 DB 一致
        if (Objects.equals(member.getAdmitStatus(), 1)) {
            redisTemplate.opsForValue().decrement(ACTIVE_COUNT_KEY + conferenceId);
        }

        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        if (callId != null) {
            callService.leaveConference(userId, callId);
        }

        if (conference != null) {
            Map<String, Object> leavePayload = new HashMap<>();
            leavePayload.put("conferenceId", conferenceId);
            leavePayload.put("conversationId", conference.getConversationId());
            leavePayload.put("userId", userId);
            leavePayload.put("callId", callId != null ? callId : "");
            leavePayload.put("participantCount", countAdmitted(conferenceId));
            broadcastToActiveMembers(conferenceId, "conference_leave", leavePayload);
            notifyConversationPresence(conference);
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
        }
        redisTemplate.delete(CALL_ID_KEY + conferenceId);
        // 会议结束清理活跃计数 key，避免残留
        redisTemplate.delete(ACTIVE_COUNT_KEY + conferenceId);
        notifyConversationEnded(conference, callId);
        emitConferenceEndedMessage(userId, conference);
    }

    @Override
    @Transactional(readOnly = true)
    public ConferenceInfoVO info(Long userId, Long conferenceId) {
        Conference conference = conferenceMapper.selectOneById(conferenceId);
        if (conference == null) {
            throw new CustomException(404, "会议不存在");
        }
        chatService.assertConversationMember(userId, conference.getConversationId());
        return toInfo(conference, redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConferenceInfoVO> listActive(Long userId) {
        List<ConferenceMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getUserId).eq(userId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集去重后的会议ID（保持 membership 顺序）
        List<Long> conferenceIds = memberships.stream()
                .map(ConferenceMember::getConferenceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (conferenceIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询会议，避免 N+1；分批防止 IN 列表过长
        Map<Long, Conference> conferenceMap = new HashMap<>();
        int batchSize = 100;
        for (int i = 0; i < conferenceIds.size(); i += batchSize) {
            List<Long> batch = conferenceIds.subList(i, Math.min(i + batchSize, conferenceIds.size()));
            List<Conference> batchConfs = conferenceMapper.selectListByIds(batch);
            for (Conference c : batchConfs) {
                if (c != null) {
                    conferenceMap.put(c.getId(), c);
                }
            }
        }

        // 过滤 ACTIVE，保持原 membership 顺序
        List<Conference> activeConfs = conferenceIds.stream()
                .map(conferenceMap::get)
                .filter(Objects::nonNull)
                .filter(c -> Objects.equals(c.getStatus(), Conference.STATUS_ACTIVE))
                .collect(Collectors.toList());
        if (activeConfs.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询 Redis callId，避免 N 次 get
        List<String> redisKeys = activeConfs.stream()
                .map(c -> CALL_ID_KEY + c.getId())
                .collect(Collectors.toList());
        List<String> callIds = redisTemplate.opsForValue().multiGet(redisKeys);

        // 组装结果，保持返回值结构不变
        List<ConferenceInfoVO> result = new ArrayList<>(activeConfs.size());
        for (int i = 0; i < activeConfs.size(); i++) {
            Conference c = activeConfs.get(i);
            String callId = (callIds != null && i < callIds.size()) ? callIds.get(i) : null;
            result.add(toInfo(c, callId, userId));
        }
        return result;
    }

    @Override
    public ConferenceInfoVO findActiveInConversation(Long userId, Long conversationId) {
        chatService.assertConversationMember(userId, conversationId);
        List<Conference> actives = conferenceMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Conference::getConversationId).eq(conversationId)
                        .and(Conference::getStatus).eq(Conference.STATUS_ACTIVE)
                        .orderBy(Conference::getStartTime, false)
                        .limit(1)
        );
        if (actives.isEmpty()) {
            return null;
        }
        Conference conference = actives.get(0);
        return toInfo(conference, redisTemplate.opsForValue().get(CALL_ID_KEY + conference.getId()), userId);
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

    /** 向当前仍在会中且已准入的成员推送同一事件（事务提交后推送，避免幽灵事件） */
    private void broadcastToActiveMembers(Long conferenceId, String action, Map<String, Object> payload) {
        runAfterCommit(() -> {
            List<ConferenceMember> members = memberMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ConferenceMember::getConferenceId).eq(conferenceId)
                            .and(ConferenceMember::getLeftFlag).eq(0)
                            .and(ConferenceMember::getAdmitStatus).eq(1)
            );
            for (ConferenceMember m : members) {
                pushService.pushToUser(m.getUserId(), action, payload);
            }
        });
    }

    @Override
    @Transactional
    public void removeMember(Long hostId, Long conferenceId, Long targetUserId) {
        requireHostOrCoHost(conferenceId, hostId);
        Conference conference = conferenceMapper.selectOneById(conferenceId);
        leave(targetUserId, conferenceId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("conferenceId", conferenceId);
        if (conference != null && conference.getConversationId() != null) {
            payload.put("conversationId", conference.getConversationId());
        }
        pushService.pushToUser(targetUserId, "conference_remove", payload);
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
        // 用 Redis 原子计数器替代 DB selectCount + update 的 check-then-act 竞态，防止并发准入超限
        String countKey = ACTIVE_COUNT_KEY + conferenceId;
        Long newCount = redisTemplate.opsForValue().increment(countKey);
        if (newCount != null && newCount > conference.getMaxParticipants()) {
            redisTemplate.opsForValue().decrement(countKey);
            throw new CustomException(400, "会议人数已满（上限 " + conference.getMaxParticipants() + " 人）");
        }
        if (newCount != null) {
            redisTemplate.expire(countKey, ACTIVE_COUNT_TTL);
        }
        target.setAdmitStatus(1);
        memberMapper.update(target);
        String callId = ensureAndGetCallId(conference, hostId);
        callService.joinConference(targetUserId, callId);
        broadcastToActiveMembers(conferenceId, "conference_admit", Map.of(
                "conferenceId", conferenceId,
                "userId", targetUserId,
                "callId", callId
        ));
        notifyConversationPresence(conference);
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
        // 从等候室自动准入时递增活跃计数，保持 Redis 计数与 DB 一致
        boolean wasAdmitted = Objects.equals(target.getAdmitStatus(), 1);
        target.setRole(normalized);
        target.setAdmitStatus(1);
        memberMapper.update(target);
        if (!wasAdmitted) {
            redisTemplate.opsForValue().increment(ACTIVE_COUNT_KEY + conferenceId);
        }
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
    @Transactional(readOnly = true)
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
                .scene(StringUtils.hasText(conference.getScene()) ? conference.getScene() : Conference.SCENE_MEETING)
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
        runAfterCommit(() -> {
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
        });
    }

    private String requireCallId(Long conferenceId) {
        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conferenceId);
        if (callId == null || callId.isBlank()) {
            throw new CustomException(404, "会议信令通道不存在或已过期");
        }
        return callId;
    }

    private String ensureAndGetCallId(Conference conference, Long actorUserId) {
        ensureCallChannel(conference, actorUserId);
        return requireCallId(conference.getId());
    }

    /**
     * 确保 ACTIVE 会议的 Redis 信令通道可用；过期/已结束则重建。
     */
    private void ensureCallChannel(Conference conference, Long actorUserId) {
        String mapped = redisTemplate.opsForValue().get(CALL_ID_KEY + conference.getId());
        boolean alive = false;
        if (mapped != null && !mapped.isBlank()) {
            Map<Object, Object> data = redisTemplate.opsForHash().entries("linkx:call:" + mapped);
            if (!data.isEmpty()) {
                String status = data.get("status") != null ? String.valueOf(data.get("status")) : "";
                alive = !"ended".equalsIgnoreCase(status) && !"cancelled".equalsIgnoreCase(status);
            }
        }
        if (alive) {
            redisTemplate.expire(CALL_ID_KEY + conference.getId(), Duration.ofHours(4));
            return;
        }
        String callId = callService.createConference(
                actorUserId,
                conference.getConversationId(),
                conference.getType(),
                conference.getId(),
                conference.getTitle(),
                StringUtils.hasText(conference.getPassword()),
                conference.getScene());
        redisTemplate.opsForValue().set(CALL_ID_KEY + conference.getId(), callId, Duration.ofHours(4));
    }

    /** 强制结束会议（不校验主持人，用于收口僵尸 ACTIVE） */
    private void forceEndConference(Conference conference) {
        if (conference == null || !Objects.equals(conference.getStatus(), Conference.STATUS_ACTIVE)) {
            return;
        }
        conference.setStatus(Conference.STATUS_ENDED);
        conference.setEndTime(new Date());
        conference.setUpdateTime(new Date());
        conferenceMapper.update(conference);

        List<ConferenceMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conference.getId())
                        .and(ConferenceMember::getLeftFlag).eq(0)
        );
        String callId = redisTemplate.opsForValue().get(CALL_ID_KEY + conference.getId());
        for (ConferenceMember m : members) {
            m.setLeftFlag(1);
            m.setLeaveTime(new Date());
            memberMapper.update(m);
            if (callId != null) {
                try {
                    callService.leaveConference(m.getUserId(), callId);
                } catch (Exception ignored) {
                    /* 通道可能已失效 */
                }
            }
        }
        redisTemplate.delete(CALL_ID_KEY + conference.getId());
        // 强制结束会议清理活跃计数 key，避免残留
        redisTemplate.delete(ACTIVE_COUNT_KEY + conference.getId());
        notifyConversationEnded(conference, callId);
        emitConferenceEndedMessage(null, conference);
    }

    private long countAdmitted(Long conferenceId) {
        return memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ConferenceMember::getConferenceId).eq(conferenceId)
                        .and(ConferenceMember::getLeftFlag).eq(0)
                        .and(ConferenceMember::getAdmitStatus).eq(1)
        );
    }

    /** 同步会话顶栏：进行中会议人数变化（事务提交后推送，避免幽灵事件） */
    private void notifyConversationPresence(Conference conference) {
        if (conference == null || conference.getConversationId() == null || conference.getId() == null) {
            return;
        }
        final Long conferenceId = conference.getId();
        final Long conversationId = conference.getConversationId();
        runAfterCommit(() -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("conferenceId", conferenceId);
            payload.put("conversationId", conversationId);
            payload.put("title", conference.getTitle());
            payload.put("type", conference.getType());
            payload.put("scene", StringUtils.hasText(conference.getScene()) ? conference.getScene() : Conference.SCENE_MEETING);
            payload.put("hasPassword", StringUtils.hasText(conference.getPassword()));
            payload.put("participantCount", countAdmitted(conferenceId));
            payload.put("status", "active");
            pushService.pushActionToConversationMembers(
                    conversationId, "conference_presence", payload);
        });
    }

    /** 同步会话顶栏：会议结束（事务提交后推送，避免幽灵事件） */
    private void notifyConversationEnded(Conference conference, String callId) {
        if (conference == null || conference.getConversationId() == null) {
            return;
        }
        final Long conversationId = conference.getConversationId();
        final Long conferenceId = conference.getId();
        final String callIdVal = callId != null ? callId : "";
        runAfterCommit(() -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("conferenceId", conferenceId);
            payload.put("conversationId", conversationId);
            payload.put("callId", callIdVal);
            pushService.pushActionToConversationMembers(
                    conversationId, "conference_end", payload);
        });
    }
}
