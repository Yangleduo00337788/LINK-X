package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.vo.CallInviteVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.service.CallService;
import com.linkx.server.service.ChatService;
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

    private final ChatService chatService;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final MessageNotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

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

        String content = "voice".equals(callType) ? "邀请你进行语音通话" : "邀请你进行视频通话";
        notificationService.create(
                peerId,
                userId,
                null,
                null,
                "call_" + callType,
                conversationId,
                content
        );

        return CallInviteVO.builder()
                .callId(callId)
                .conversationId(conversationId)
                .callType(callType)
                .status("ringing")
                .build();
    }

    @Override
    public void cancel(Long userId, CallCancelDTO dto) {
        String key = callKey(dto.getCallId());
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            throw new CustomException(404, "通话不存在或已结束");
        }
        if (!String.valueOf(userId).equals(String.valueOf(data.get("callerId")))) {
            throw new CustomException(403, "无权取消该通话");
        }
        redisTemplate.opsForHash().put(key, "status", "cancelled");
        redisTemplate.expire(key, Duration.ofMinutes(1));
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
}
