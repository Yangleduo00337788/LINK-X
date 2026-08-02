package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallIdDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.vo.CallEventVO;
import com.linkx.server.controller.vo.CallInviteVO;
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
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.RateLimitService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CallServiceImpl 通话服务")
class CallServiceImplTest {

    private static final long CALLER = 1L;
    private static final long CALLEE = 2L;
    private static final long CONV = 100L;
    private static final long CONF_ID = 200L;
    private static final String CALL_ID = "abc123";
    private static final String CALL_KEY = "linkx:call:" + CALL_ID;

    @Mock ChatService chatService;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MessageNotificationService notificationService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ImMessagePushService pushService;
    @Mock MediaUrlService mediaUrlService;
    @Mock RateLimitService rateLimitService;
    @Mock HashOperations<String, Object, Object> hashOps;
    @Mock SetOperations<String, String> setOps;

    private CallServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(chatService.postCallInviteMessage(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(MessageVO.builder().id(9L).type("conference").build());
        when(chatService.updateCallTipMessage(anyLong(), anyString(), anyString()))
                .thenReturn(MessageVO.builder().id(9L).build());
        service = new CallServiceImpl(
                chatService, conversationMapper, memberMapper, sysUserMapper,
                notificationService, redisTemplate, pushService, mediaUrlService, rateLimitService);
    }

    private SysUser user(long id, String nick) {
        return SysUser.builder().id(id).username("u" + id).nickname(nick).avatar("av" + id).build();
    }

    private ImConversationMember member(long userId) {
        return ImConversationMember.builder().conversationId(CONV).userId(userId).build();
    }

    private Map<Object, Object> ringingCall() {
        return Map.of(
                "callerId", "1",
                "calleeId", "2",
                "conversationId", "100",
                "callType", "voice",
                "status", "ringing");
    }

    private Map<Object, Object> acceptedCall() {
        Map<Object, Object> map = new HashMap<>(ringingCall());
        map.put("status", "accepted");
        map.put("acceptedAt", String.valueOf(System.currentTimeMillis() - 10_000L));
        return map;
    }

    private Map<Object, Object> conferenceCall() {
        Map<Object, Object> map = new HashMap<>(ringingCall());
        map.put("isConference", "true");
        map.put("scene", "meeting");
        return map;
    }

    private CallIdDTO callIdDto() {
        CallIdDTO dto = new CallIdDTO();
        dto.setCallId(CALL_ID);
        return dto;
    }

    private void stubPrivateConversation() {
        doNothing().when(chatService).assertConversationMember(CALLER, CONV);
        when(conversationMapper.selectOneById(CONV)).thenReturn(
                ImConversation.builder().id(CONV).type(ImConversation.TYPE_PRIVATE).build());
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                member(CALLER), member(CALLEE)));
        when(sysUserMapper.selectOneById(CALLER)).thenReturn(user(CALLER, "主叫"));
        when(sysUserMapper.selectOneById(CALLEE)).thenReturn(user(CALLEE, "被叫"));
    }

    @Nested
    @DisplayName("invite 发起通话")
    class InviteTests {

        @Test
        @DisplayName("非私聊会话返回 400")
        void notPrivateType() {
            CallInviteDTO dto = new CallInviteDTO();
            dto.setConversationId(CONV);
            dto.setCallType("voice");
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(conversationMapper.selectOneById(CONV)).thenReturn(
                    ImConversation.builder().id(CONV).type(ImConversation.TYPE_GROUP).build());

            CustomException ex = assertThrows(CustomException.class, () -> service.invite(CALLER, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("私聊发起成功：通知、Redis、提示消息")
        void successPrivate() {
            stubPrivateConversation();
            CallInviteDTO dto = new CallInviteDTO();
            dto.setConversationId(CONV);
            dto.setCallType("voice");

            CallInviteVO vo = service.invite(CALLER, dto);

            assertNotNull(vo.getCallId());
            assertEquals(CONV, vo.getConversationId());
            assertEquals("voice", vo.getCallType());
            assertEquals("ringing", vo.getStatus());
            assertEquals(CALLEE, vo.getPeerUserId());
            assertEquals("被叫", vo.getPeerNickname());
            assertEquals("https://cdn/av2", vo.getPeerAvatar());

            verify(notificationService).create(
                    eq(CALLEE), eq(CALLER), eq("主叫"), eq("av1"),
                    eq("call_voice"), eq(CONV), eq("邀请你进行语音通话"));
            verify(hashOps).putAll(startsWith("linkx:call:"), anyMap());
            verify(redisTemplate).expire(startsWith("linkx:call:"), eq(Duration.ofMinutes(5)));
            verify(chatService).postCallInviteMessage(eq(CALLER), eq(CONV), anyString(), eq("voice"));
            verify(hashOps).put(startsWith("linkx:call:"), eq("tipMessageId"), eq("9"));
            verify(pushService).pushToUser(eq(CALLEE), eq("call_invite"), any(CallEventVO.class));
        }
    }

    @Nested
    @DisplayName("cancel 取消通话")
    class CancelTests {

        @Test
        @DisplayName("振铃中主叫取消成功")
        void ringingSuccessAsCaller() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            CallCancelDTO dto = new CallCancelDTO();
            dto.setCallId(CALL_ID);

            service.cancel(CALLER, dto);

            verify(hashOps).put(CALL_KEY, "status", "cancelled");
            verify(redisTemplate).expire(CALL_KEY, Duration.ofMinutes(1));
            verify(chatService).updateCallTipMessage(CONV, CALL_ID, "语音通话 已取消");
            verify(pushService).pushToUser(eq(CALLEE), eq("call_cancel"), any(CallEventVO.class));
        }
    }

    @Nested
    @DisplayName("accept 接听")
    class AcceptTests {

        @Test
        @DisplayName("振铃中被叫接听成功")
        void ringingAsCallee() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());

            service.accept(CALLEE, callIdDto());

            verify(hashOps).put(CALL_KEY, "status", "accepted");
            verify(hashOps).put(eq(CALL_KEY), eq("acceptedAt"), anyString());
            verify(redisTemplate).expire(CALL_KEY, Duration.ofMinutes(5));
            verify(pushService).pushToUser(eq(CALLER), eq("call_accept"), any(CallEventVO.class));
        }
    }

    @Nested
    @DisplayName("reject 拒绝")
    class RejectTests {

        @Test
        @DisplayName("振铃中被叫拒绝成功")
        void ringingAsCallee() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());

            service.reject(CALLEE, callIdDto());

            verify(hashOps).put(CALL_KEY, "status", "rejected");
            verify(chatService).updateCallTipMessage(CONV, CALL_ID, "语音通话 已拒绝");
            verify(pushService).pushToUser(eq(CALLER), eq("call_reject"), any(CallEventVO.class));
        }
    }

    @Nested
    @DisplayName("hangup 挂断")
    class HangupTests {

        @Test
        @DisplayName("已接通挂断计算时长并结束")
        void acceptedComputesDuration() {
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());

            service.hangup(CALLER, callIdDto());

            verify(hashOps).put(CALL_KEY, "status", "ended");
            verify(chatService).updateCallTipMessage(eq(CONV), eq(CALL_ID), contains("语音通话"));
            verify(pushService).pushToUser(eq(CALLEE), eq("call_hangup"), any(CallEventVO.class));
        }

        @Test
        @DisplayName("振铃中主叫挂断等同取消")
        void ringingAsCallerCancels() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());

            service.hangup(CALLER, callIdDto());

            verify(hashOps).put(CALL_KEY, "status", "cancelled");
            verify(chatService).updateCallTipMessage(CONV, CALL_ID, "语音通话 已取消");
            verify(pushService).pushToUser(eq(CALLEE), eq("call_hangup"), any(CallEventVO.class));
        }
    }

    @Nested
    @DisplayName("signal 信令中继")
    class SignalTests {

        @Test
        @DisplayName("1:1 已接通信令转发给对端")
        void oneToOneAcceptedRelaysToPeer() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());
            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSignalType("offer");
            dto.setSdp("v=0");

            service.signal(CALLER, dto);

            verify(rateLimitService).check(
                    eq("call:signal:rate:" + CALLER), eq(10), eq(1), eq("信令发送过于频繁，请稍后再试"));
            ArgumentCaptor<CallEventVO> captor = ArgumentCaptor.forClass(CallEventVO.class);
            verify(pushService).pushToUser(eq(CALLEE), eq("call_signal"), captor.capture());
            assertEquals(CALL_ID, captor.getValue().getCallId());
            assertEquals("offer", captor.getValue().getSignalType());
            assertEquals("v=0", captor.getValue().getSdp());
        }
    }

    @Nested
    @DisplayName("reconnect 断线重连")
    class ReconnectTests {

        @Test
        @DisplayName("已接通重连通知对端")
        void acceptedNotifiesPeer() {
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());

            service.reconnect(CALLER, CALL_ID);

            verify(redisTemplate).expire(CALL_KEY, Duration.ofMinutes(5));
            verify(pushService).pushToUser(eq(CALLEE), eq("call_reconnect"), anyMap());
        }
    }

    @Nested
    @DisplayName("createConference 创建会议")
    class CreateConferenceTests {

        @Test
        @DisplayName("成功创建并通知会话成员")
        void successAddsParticipantAndNotifies() {
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(sysUserMapper.selectOneById(CALLER)).thenReturn(user(CALLER, "主持人"));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    member(CALLER), member(CALLEE), member(3L)));

            String callId = service.createConference(CALLER, CONV, "video");

            assertNotNull(callId);
            String participantsKey = "linkx:call:" + callId + ":participants";
            verify(setOps).add(participantsKey, String.valueOf(CALLER));
            verify(redisTemplate).expire(participantsKey, Duration.ofHours(4));
            verify(pushService).pushToUser(eq(CALLEE), eq("conference_invite"), anyMap());
            verify(pushService).pushToUser(eq(3L), eq("conference_invite"), anyMap());
            verify(pushService, never()).pushToUser(eq(CALLER), eq("conference_invite"), any());
        }
    }

    @Nested
    @DisplayName("joinConference / leaveConference 入会离会")
    class ConferenceJoinLeaveTests {

        @Test
        @DisplayName("加入会议并通知其他参与者")
        void joinConference() {
            when(hashOps.entries(CALL_KEY)).thenReturn(conferenceCall());
            doNothing().when(chatService).assertConversationMember(CALLEE, CONV);
            when(setOps.members("linkx:call:" + CALL_ID + ":participants"))
                    .thenReturn(new HashSet<>(Set.of("1", "2")));

            service.joinConference(CALLEE, CALL_ID);

            verify(setOps).add("linkx:call:" + CALL_ID + ":participants", String.valueOf(CALLEE));
            verify(pushService).pushToUser(eq(CALLER), eq("conference_join"), anyMap());
        }

        @Test
        @DisplayName("最后一人离会结束通话")
        void lastLeaveEndsCall() {
            String participantsKey = "linkx:call:" + CALL_ID + ":participants";
            when(setOps.members(participantsKey)).thenReturn(Set.of(String.valueOf(CALLEE)));
            when(setOps.size(participantsKey)).thenReturn(0L);

            service.leaveConference(CALLEE, CALL_ID);

            verify(setOps).remove(participantsKey, String.valueOf(CALLEE));
            verify(hashOps).put(CALL_KEY, "status", "ended");
        }
    }

    @Nested
    @DisplayName("getConferenceParticipants 参会者列表")
    class GetConferenceParticipantsTests {

        @Test
        @DisplayName("返回参会用户映射")
        void returnsUserMaps() {
            when(hashOps.entries(CALL_KEY)).thenReturn(conferenceCall());
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(setOps.members("linkx:call:" + CALL_ID + ":participants"))
                    .thenReturn(Set.of("1", "2"));
            when(sysUserMapper.selectOneById(CALLER)).thenReturn(user(CALLER, "主叫"));
            when(sysUserMapper.selectOneById(CALLEE)).thenReturn(user(CALLEE, "被叫"));

            List<Map<String, Object>> participants = service.getConferenceParticipants(CALLER, CALL_ID);

            assertEquals(2, participants.size());
            Set<Object> ids = participants.stream().map(p -> p.get("userId")).collect(Collectors.toSet());
            assertTrue(ids.contains(CALLER));
            assertTrue(ids.contains(CALLEE));
            Map<String, Object> callerRow = participants.stream()
                    .filter(p -> Objects.equals(CALLER, p.get("userId")))
                    .findFirst().orElseThrow();
            assertEquals("主叫", callerRow.get("nickname"));
            assertEquals("https://cdn/av1", callerRow.get("avatar"));
        }

        @Test
        @DisplayName("空参与者列表")
        void emptyParticipants() {
            when(hashOps.entries(CALL_KEY)).thenReturn(conferenceCall());
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(setOps.members("linkx:call:" + CALL_ID + ":participants")).thenReturn(null);

            assertTrue(service.getConferenceParticipants(CALLER, CALL_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("extended coverage")
    class ExtendedCoverage {
        @Test
        @DisplayName("invite 会话不存在")
        void inviteConversationMissing() {
            CallInviteDTO dto = new CallInviteDTO();
            dto.setConversationId(CONV);
            dto.setCallType("video");
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(conversationMapper.selectOneById(CONV)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.invite(CALLER, dto));
        }

        @Test
        @DisplayName("invite 视频通话文案")
        void inviteVideo() {
            stubPrivateConversation();
            CallInviteDTO dto = new CallInviteDTO();
            dto.setConversationId(CONV);
            dto.setCallType("video");
            CallInviteVO vo = service.invite(CALLER, dto);
            assertEquals("video", vo.getCallType());
            verify(notificationService).create(
                    eq(CALLEE), eq(CALLER), anyString(), any(), eq("call_video"), eq(CONV),
                    eq("邀请你进行视频通话"));
        }

        @Test
        @DisplayName("cancel 非主叫拒绝")
        void cancelNotCaller() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            CallCancelDTO dto = new CallCancelDTO();
            dto.setCallId(CALL_ID);
            assertThrows(CustomException.class, () -> service.cancel(CALLEE, dto));
        }

        @Test
        @DisplayName("cancel 未接听标记 missed")
        void cancelMissed() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            CallCancelDTO dto = new CallCancelDTO();
            dto.setCallId(CALL_ID);
            dto.setReason("timeout");
            service.cancel(CALLER, dto);
            verify(hashOps).put(CALL_KEY, "status", "missed");
            verify(chatService).updateCallTipMessage(CONV, CALL_ID, "语音通话 未接听");
        }

        @Test
        @DisplayName("accept 非被叫拒绝")
        void acceptNotCallee() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            assertThrows(CustomException.class, () -> service.accept(CALLER, callIdDto()));
        }

        @Test
        @DisplayName("accept 已结束")
        void acceptNotRinging() {
            Map<Object, Object> ended = new HashMap<>(ringingCall());
            ended.put("status", "ended");
            when(hashOps.entries(CALL_KEY)).thenReturn(ended);
            assertThrows(CustomException.class, () -> service.accept(CALLEE, callIdDto()));
        }

        @Test
        @DisplayName("reject 非被叫拒绝")
        void rejectNotCallee() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            assertThrows(CustomException.class, () -> service.reject(CALLER, callIdDto()));
        }

        @Test
        @DisplayName("hangup 被叫振铃等同拒绝")
        void hangupCalleeWhileRinging() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            service.hangup(CALLEE, callIdDto());
            verify(hashOps).put(CALL_KEY, "status", "rejected");
            verify(chatService).updateCallTipMessage(CONV, CALL_ID, "语音通话 已拒绝");
        }

        @Test
        @DisplayName("hangup 已结束幂等")
        void hangupAlreadyEnded() {
            Map<Object, Object> ended = new HashMap<>(ringingCall());
            ended.put("status", "ended");
            when(hashOps.entries(CALL_KEY)).thenReturn(ended);
            service.hangup(CALLER, callIdDto());
            verify(pushService, never()).pushToUser(anyLong(), eq("call_hangup"), any());
        }

        @Test
        @DisplayName("hangup 无权操作")
        void hangupUnauthorized() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            assertThrows(CustomException.class, () -> service.hangup(99L, callIdDto()));
        }

        @Test
        @DisplayName("signal 通话已结束")
        void signalCallEnded() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            Map<Object, Object> ended = new HashMap<>(ringingCall());
            ended.put("status", "ended");
            when(hashOps.entries(CALL_KEY)).thenReturn(ended);
            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSignalType("offer");
            assertThrows(CustomException.class, () -> service.signal(CALLER, dto));
        }

        @Test
        @DisplayName("signal 无权发送")
        void signalUnauthorized() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());
            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSignalType("offer");
            assertThrows(CustomException.class, () -> service.signal(99L, dto));
        }

        @Test
        @DisplayName("signal SDP 过大")
        void signalSdpTooLarge() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSdp("x".repeat(70000));
            assertThrows(CustomException.class, () -> service.signal(CALLER, dto));
        }

        @Test
        @DisplayName("signal 会议广播")
        void signalConferenceBroadcast() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            Map<Object, Object> conf = conferenceCall();
            conf.put("status", "accepted");
            when(hashOps.entries(CALL_KEY)).thenReturn(conf);
            when(setOps.isMember("linkx:call:" + CALL_ID + ":participants", String.valueOf(CALLER))).thenReturn(true);
            when(setOps.members("linkx:call:" + CALL_ID + ":participants"))
                    .thenReturn(new HashSet<>(Set.of("1", "2", "3")));

            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSignalType("offer");
            service.signal(CALLER, dto);

            verify(pushService, times(2)).pushToUser(anyLong(), eq("call_signal"), any(CallEventVO.class));
        }

        @Test
        @DisplayName("signal 会议目标不在会中")
        void signalConferenceTargetMissing() {
            doNothing().when(rateLimitService).check(anyString(), anyInt(), anyInt(), anyString());
            when(hashOps.entries(CALL_KEY)).thenReturn(conferenceCall());
            when(setOps.isMember("linkx:call:" + CALL_ID + ":participants", String.valueOf(CALLER))).thenReturn(true);
            when(setOps.isMember("linkx:call:" + CALL_ID + ":participants", "99")).thenReturn(false);

            CallSignalDTO dto = new CallSignalDTO();
            dto.setCallId(CALL_ID);
            dto.setSignalType("offer");
            dto.setTargetUserId(99L);
            assertThrows(CustomException.class, () -> service.signal(CALLER, dto));
        }

        @Test
        @DisplayName("reconnect 通话已结束")
        void reconnectEnded() {
            Map<Object, Object> ended = new HashMap<>(ringingCall());
            ended.put("status", "cancelled");
            when(hashOps.entries(CALL_KEY)).thenReturn(ended);
            assertThrows(CustomException.class, () -> service.reconnect(CALLER, CALL_ID));
        }

        @Test
        @DisplayName("reconnect 无权重连")
        void reconnectUnauthorized() {
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());
            assertThrows(CustomException.class, () -> service.reconnect(99L, CALL_ID));
        }

        @Test
        @DisplayName("switchDevice 成功")
        void switchDeviceOk() {
            when(hashOps.entries(CALL_KEY)).thenReturn(acceptedCall());
            service.switchDevice(CALLER, CALL_ID, "camera", false);
            verify(hashOps).put("linkx:call:" + CALL_ID + ":device:" + CALLER, "camera", "false");
            verify(pushService).pushToUser(eq(CALLEE), eq("call_device_switch"), anyMap());
        }

        @Test
        @DisplayName("switchDevice 未接通")
        void switchDeviceNotConnected() {
            when(hashOps.entries(CALL_KEY)).thenReturn(ringingCall());
            assertThrows(CustomException.class,
                    () -> service.switchDevice(CALLER, CALL_ID, "mic", true));
        }

        @Test
        @DisplayName("createConference 带 conferenceId 发通知")
        void createConferenceWithId() {
            doNothing().when(chatService).assertConversationMember(CALLER, CONV);
            when(sysUserMapper.selectOneById(CALLER)).thenReturn(user(CALLER, "主持人"));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(member(CALLER), member(CALLEE)));

            String callId = service.createConference(CALLER, CONV, "video", CONF_ID, "周会", true, "meeting");
            assertNotNull(callId);
            verify(notificationService).create(eq(CALLEE), eq(CALLER), anyString(), any(), eq("conference_invite"),
                    eq(CONF_ID), contains("周会"));
        }

        @Test
        @DisplayName("joinConference 会议已结束")
        void joinConferenceEnded() {
            Map<Object, Object> ended = new HashMap<>(conferenceCall());
            ended.put("status", "ended");
            when(hashOps.entries(CALL_KEY)).thenReturn(ended);
            assertThrows(CustomException.class, () -> service.joinConference(CALLEE, CALL_ID));
        }

        @Test
        @DisplayName("leaveConference 仍有参与者不结束")
        void leaveConferenceStillActive() {
            String participantsKey = "linkx:call:" + CALL_ID + ":participants";
            when(setOps.members(participantsKey)).thenReturn(Set.of("1", "2"));
            when(setOps.size(participantsKey)).thenReturn(1L);

            service.leaveConference(CALLEE, CALL_ID);
            verify(hashOps, never()).put(CALL_KEY, "status", "ended");
        }
    }
}
