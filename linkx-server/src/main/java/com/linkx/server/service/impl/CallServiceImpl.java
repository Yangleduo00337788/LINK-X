package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallIdDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.vo.CallEventVO;
import com.linkx.server.controller.vo.CallInviteVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.CallService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    private static final Duration CALL_TTL = Duration.ofMinutes(5);
    private static final Duration ENDED_TTL = Duration.ofMinutes(1);

    private final ChatService chatService;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final MessageNotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ImMessagePushService pushService;
    private final MediaUrlService mediaUrlService;

    @Override
    public CallInviteVO invite(Long userId, CallInviteDTO dto) {
        Long conversationId = dto.getConversationId();
        chatService.assertConversationMember(userId, conversationId);

        ImConversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation == null) {
            throw new CustomException(404, "会话不存在");
        }
        if (conversation.getType() != ImConversation.TYPE_PRIVATE) {
            throw new CustomException(400, "暂仅支持单聊语音/视频通话");
        }

        Long peerId = resolvePrivatePeerId(userId, conversationId);
        String callType = dto.getCallType();
        String callId = UUID.randomUUID().toString().replace("-", "");
        String key = callKey(callId);

        redisTemplate.opsForHash().putAll(key, Map.of(
                "callerId", String.valueOf(userId),
                "calleeId", String.valueOf(peerId),
                "conversationId", String.valueOf(conversationId),
                "callType", callType,
                "status", "ringing"
        ));
        redisTemplate.expire(key, CALL_TTL);

        SysUser caller = sysUserMapper.selectOneById(userId);
        SysUser peer = sysUserMapper.selectOneById(peerId);
        String callerName = displayName(caller);
        String callerAvatar = caller != null ? nullToEmpty(mediaUrlService.resolve(caller.getAvatar())) : "";

        String content = "voice".equals(callType) ? "邀请你进行语音通话" : "邀请你进行视频通话";
        // 通知表存原始 key；列表出口再签发。这里传 raw，避免把长签名 URL 写入 DB
        notificationService.create(
                peerId,
                userId,
                callerName,
                caller != null ? caller.getAvatar() : null,
                "call_" + callType,
                conversationId,
                content
        );

        CallEventVO event = CallEventVO.builder()
                .callId(callId)
                .conversationId(conversationId)
                .callType(callType)
                .status("ringing")
                .fromUserId(userId)
                .toUserId(peerId)
                .fromNickname(callerName)
                .fromAvatar(callerAvatar)
                .build();
        pushService.pushToUser(peerId, "call_invite", event);

        return CallInviteVO.builder()
                .callId(callId)
                .conversationId(conversationId)
                .callType(callType)
                .status("ringing")
                .peerUserId(peerId)
                .peerNickname(displayName(peer))
                .peerAvatar(peer != null ? nullToEmpty(mediaUrlService.resolve(peer.getAvatar())) : "")
                .build();
    }

    @Override
    public void cancel(Long userId, CallCancelDTO dto) {
        Map<Object, Object> data = requireCall(dto.getCallId());
        assertCaller(userId, data);
        String status = str(data.get("status"));
        if (!"ringing".equals(status)) {
            throw new CustomException(400, "通话已不在振铃状态");
        }
        updateStatus(dto.getCallId(), "cancelled");
        Long peerId = Long.parseLong(str(data.get("calleeId")));
        pushService.pushToUser(peerId, "call_cancel", buildEvent(dto.getCallId(), data, userId, "cancelled"));
    }

    @Override
    public void accept(Long userId, CallIdDTO dto) {
        Map<Object, Object> data = requireCall(dto.getCallId());
        assertCallee(userId, data);
        if (!"ringing".equals(str(data.get("status")))) {
            throw new CustomException(400, "通话已结束或已被处理");
        }
        updateStatus(dto.getCallId(), "accepted");
        redisTemplate.expire(callKey(dto.getCallId()), CALL_TTL);
        Long callerId = Long.parseLong(str(data.get("callerId")));
        pushService.pushToUser(callerId, "call_accept", buildEvent(dto.getCallId(), data, userId, "accepted"));
    }

    @Override
    public void reject(Long userId, CallIdDTO dto) {
        Map<Object, Object> data = requireCall(dto.getCallId());
        assertCallee(userId, data);
        if (!"ringing".equals(str(data.get("status")))) {
            throw new CustomException(400, "通话已结束或已被处理");
        }
        updateStatus(dto.getCallId(), "rejected");
        Long callerId = Long.parseLong(str(data.get("callerId")));
        pushService.pushToUser(callerId, "call_reject", buildEvent(dto.getCallId(), data, userId, "rejected"));
    }

    @Override
    public void hangup(Long userId, CallIdDTO dto) {
        Map<Object, Object> data = requireCall(dto.getCallId());
        Long callerId = Long.parseLong(str(data.get("callerId")));
        Long calleeId = Long.parseLong(str(data.get("calleeId")));
        if (!userId.equals(callerId) && !userId.equals(calleeId)) {
            throw new CustomException(403, "无权操作该通话");
        }
        String status = str(data.get("status"));
        if ("ended".equals(status) || "cancelled".equals(status) || "rejected".equals(status)) {
            return;
        }
        updateStatus(dto.getCallId(), "ended");
        Long peerId = userId.equals(callerId) ? calleeId : callerId;
        pushService.pushToUser(peerId, "call_hangup", buildEvent(dto.getCallId(), data, userId, "ended"));
    }

    @Override
    public void signal(Long userId, CallSignalDTO dto) {
        Map<Object, Object> data = requireCall(dto.getCallId());
        Long callerId = Long.parseLong(str(data.get("callerId")));
        Long calleeId = Long.parseLong(str(data.get("calleeId")));
        if (!userId.equals(callerId) && !userId.equals(calleeId)) {
            throw new CustomException(403, "无权发送信令");
        }
        String status = str(data.get("status"));
        if (!"accepted".equals(status) && !"connected".equals(status) && !"ringing".equals(status)) {
            throw new CustomException(400, "通话已结束，无法中继信令");
        }
        Long peerId = userId.equals(callerId) ? calleeId : callerId;
        CallEventVO event = CallEventVO.builder()
                .callId(dto.getCallId())
                .conversationId(Long.parseLong(str(data.get("conversationId"))))
                .callType(str(data.get("callType")))
                .status(status)
                .fromUserId(userId)
                .toUserId(peerId)
                .signalType(dto.getSignalType())
                .sdp(dto.getSdp())
                .candidate(dto.getCandidate())
                .build();
        pushService.pushToUser(peerId, "call_signal", event);
    }

    private CallEventVO buildEvent(String callId, Map<Object, Object> data, Long fromUserId, String status) {
        SysUser from = sysUserMapper.selectOneById(fromUserId);
        Long callerId = Long.parseLong(str(data.get("callerId")));
        Long calleeId = Long.parseLong(str(data.get("calleeId")));
        Long toUserId = fromUserId.equals(callerId) ? calleeId : callerId;
        return CallEventVO.builder()
                .callId(callId)
                .conversationId(Long.parseLong(str(data.get("conversationId"))))
                .callType(str(data.get("callType")))
                .status(status)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .fromNickname(displayName(from))
                .fromAvatar(from != null ? nullToEmpty(mediaUrlService.resolve(from.getAvatar())) : "")
                .build();
    }

    private Map<Object, Object> requireCall(String callId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(callKey(callId));
        if (data == null || data.isEmpty()) {
            throw new CustomException(404, "通话不存在或已结束");
        }
        return data;
    }

    private void assertCaller(Long userId, Map<Object, Object> data) {
        if (!String.valueOf(userId).equals(str(data.get("callerId")))) {
            throw new CustomException(403, "无权取消该通话");
        }
    }

    private void assertCallee(Long userId, Map<Object, Object> data) {
        if (!String.valueOf(userId).equals(str(data.get("calleeId")))) {
            throw new CustomException(403, "无权接听该通话");
        }
    }

    private void updateStatus(String callId, String status) {
        String key = callKey(callId);
        redisTemplate.opsForHash().put(key, "status", status);
        redisTemplate.expire(key, ENDED_TTL);
    }

    private Long resolvePrivatePeerId(Long userId, Long conversationId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(conversationId)
        );
        return members.stream()
                .map(ImConversationMember::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(404, "对方用户不存在"));
    }

    private String callKey(String callId) {
        return "linkx:call:" + callId;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String displayName(SysUser user) {
        if (user == null) {
            return "用户";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername() != null ? user.getUsername() : "用户";
    }
}
