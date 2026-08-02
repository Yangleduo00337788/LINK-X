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
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConferenceServiceImpl 会议服务")
class ConferenceServiceImplTest {

    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long GUEST_ID = 3L;
    private static final Long CONV_ID = 100L;
    private static final Long CONF_ID = 200L;
    private static final String CALL_ID = "call-abc";

    @Mock ConferenceMapper conferenceMapper;
    @Mock ConferenceMemberMapper memberMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MediaUrlService mediaUrlService;
    @Mock CallService callService;
    @Mock ChatService chatService;
    @Mock ImMessagePushService pushService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HashOperations<String, Object, Object> hashOps;

    private ConferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(callService.createConference(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyBoolean(), anyString()))
                .thenReturn(CALL_ID);
        when(chatService.postConferenceInviteMessage(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(MessageVO.builder().id(1L).type("conference_invite").build());
        when(chatService.postSystemMessage(any(), anyLong(), anyString()))
                .thenReturn(MessageVO.builder().id(2L).type("system").content("ended").build());
        when(valueOps.get("linkx:conference:call:" + CONF_ID)).thenReturn(CALL_ID);
        when(hashOps.entries("linkx:call:" + CALL_ID)).thenReturn(Map.of("status", "active"));

        service = new ConferenceServiceImpl(
                conferenceMapper, memberMapper, sysUserMapper, mediaUrlService,
                callService, chatService, pushService, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private Conference activeConference() {
        return Conference.builder()
                .id(CONF_ID)
                .title("Team Sync")
                .type("video")
                .scene(Conference.SCENE_MEETING)
                .creatorId(HOST_ID)
                .conversationId(CONV_ID)
                .status(Conference.STATUS_ACTIVE)
                .maxParticipants(9)
                .lobbyEnabled(0)
                .startTime(new Date())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private ConferenceMember hostMember() {
        return ConferenceMember.builder()
                .id(10L)
                .conferenceId(CONF_ID)
                .userId(HOST_ID)
                .role(ConferenceMember.ROLE_HOST)
                .muted(0)
                .videoOff(0)
                .leftFlag(0)
                .admitStatus(1)
                .joinTime(new Date())
                .createTime(new Date())
                .build();
    }

    private ConferenceMember memberRow(Long userId, String role, int admitStatus) {
        return ConferenceMember.builder()
                .id(userId * 10)
                .conferenceId(CONF_ID)
                .userId(userId)
                .role(role)
                .muted(0)
                .videoOff(0)
                .leftFlag(0)
                .admitStatus(admitStatus)
                .joinTime(new Date())
                .createTime(new Date())
                .build();
    }

    private ConferenceCreateDTO createDto() {
        ConferenceCreateDTO dto = new ConferenceCreateDTO();
        dto.setConversationId(CONV_ID);
        dto.setType("video");
        dto.setScene(Conference.SCENE_MEETING);
        dto.setTitle("Sync");
        dto.setMaxParticipants(9);
        return dto;
    }

    private void stubParticipants(ConferenceMember... members) {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(members));
        List<Long> ids = new ArrayList<>();
        for (ConferenceMember m : members) {
            ids.add(m.getUserId());
        }
        if (ids.isEmpty()) {
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            return;
        }
        List<SysUser> users = new ArrayList<>();
        for (Long id : ids) {
            users.add(SysUser.builder().id(id).username("u" + id).nickname("Nick" + id).avatar("av" + id).build());
        }
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(users);
    }

    private void stubAdmittedCount(long count) {
        when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(count);
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新建会议")
        void createNew() {
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                Conference c = inv.getArgument(0);
                c.setId(CONF_ID);
                return 1;
            }).when(conferenceMapper).insert(any(Conference.class));

            ConferenceInfoVO vo = service.create(HOST_ID, createDto());
            assertFalse(vo.getReused());
            assertEquals(CONF_ID, vo.getId());
            assertEquals(CALL_ID, vo.getCallId());
            verify(conferenceMapper).insert(any(Conference.class));
            verify(memberMapper).insert(any(ConferenceMember.class));
            verify(callService).createConference(eq(HOST_ID), eq(CONV_ID), eq("video"), eq(CONF_ID), anyString(), eq(false), eq(Conference.SCENE_MEETING));
            verify(chatService).assertConversationMember(HOST_ID, CONV_ID);
        }

        @Test
        @DisplayName("复用同场景 ACTIVE 会议")
        void reuseSameScene() {
            Conference existing = activeConference();
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(existing);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember());
            stubParticipants(hostMember());
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(1L);

            ConferenceInfoVO vo = service.create(HOST_ID, createDto());
            assertTrue(vo.getReused());
            verify(conferenceMapper, never()).insert(any(Conference.class));
        }

        @Test
        @DisplayName("已有通话时发起会议冲突")
        void sceneConflictCallVsMeeting() {
            Conference call = activeConference();
            call.setScene(Conference.SCENE_CALL);
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(call));

            ConferenceCreateDTO dto = createDto();
            dto.setScene(Conference.SCENE_MEETING);
            CustomException ex = assertThrows(CustomException.class, () -> service.create(HOST_ID, dto));
            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains("通话"));
        }

        @Test
        @DisplayName("收口多余僵尸 ACTIVE")
        void forceEndZombieActives() {
            Conference keep = activeConference();
            Conference zombie = activeConference();
            zombie.setId(201L);
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(keep, zombie));
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(keep);
            when(conferenceMapper.selectOneById(201L)).thenReturn(zombie);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember());
            stubParticipants(hostMember());
            when(valueOps.increment(anyString())).thenReturn(1L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(hostMember()), Collections.emptyList());

            service.create(HOST_ID, createDto());
            verify(conferenceMapper, atLeastOnce()).update(argThat(c -> c.getStatus() == Conference.STATUS_ENDED));
        }

        @Test
        @DisplayName("带密码与等候室")
        void withPasswordAndLobby() {
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                Conference c = inv.getArgument(0);
                c.setId(CONF_ID);
                return 1;
            }).when(conferenceMapper).insert(any(Conference.class));

            ConferenceCreateDTO dto = createDto();
            dto.setPassword("meet123");
            dto.setLobbyEnabled(true);
            dto.setMaxParticipants(20);

            ConferenceInfoVO vo = service.create(HOST_ID, dto);
            assertTrue(vo.getHasPassword());
            assertTrue(vo.getLobbyEnabled());
            assertEquals(16, vo.getMaxParticipants());
        }
    }

    @Nested
    @DisplayName("join")
    class Join {
        @Test
        @DisplayName("会议不存在")
        void notFound() {
            when(conferenceMapper.selectOneById(999L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.join(HOST_ID, 999L, null));
        }

        @Test
        @DisplayName("会议已结束")
        void ended() {
            Conference ended = activeConference();
            ended.setStatus(Conference.STATUS_ENDED);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(ended);
            assertThrows(CustomException.class, () -> service.join(HOST_ID, CONF_ID, null));
        }

        @Test
        @DisplayName("密码错误")
        void wrongPassword() {
            Conference conf = activeConference();
            conf.setPassword(PasswordEncoderHolder.encode("secret"));
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.join(MEMBER_ID, CONF_ID, "bad"));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("明文历史密码恒定失败")
        void legacyPlainPassword() {
            Conference conf = activeConference();
            conf.setPassword("plain-old");
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            assertThrows(CustomException.class, () -> service.join(MEMBER_ID, CONF_ID, "plain-old"));
        }

        @Test
        @DisplayName("新成员直接准入")
        void newMemberAdmitted() {
            Conference conf = activeConference();
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(2L);
            stubParticipants(hostMember(), memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1));
            stubAdmittedCount(2);

            ConferenceInfoVO vo = service.join(MEMBER_ID, CONF_ID, null);
            assertEquals(CONF_ID, vo.getId());
            verify(memberMapper).insert(any(ConferenceMember.class));
            verify(callService).joinConference(MEMBER_ID, CALL_ID);
        }

        @Test
        @DisplayName("等候室等待准入")
        void lobbyWaiting() {
            Conference conf = activeConference();
            conf.setLobbyEnabled(1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            ConferenceMember waitingGuest = memberRow(GUEST_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(hostMember(), waitingGuest));

            ConferenceInfoVO vo = service.join(GUEST_ID, CONF_ID, null);
            assertTrue(vo.getWaitingAdmit());
            verify(callService, never()).joinConference(eq(GUEST_ID), anyString());
            verify(memberMapper).insert(argThat(m -> Objects.equals(m.getAdmitStatus(), 0)));
        }

        @Test
        @DisplayName("人数已满回滚计数")
        void fullConference() {
            Conference conf = activeConference();
            conf.setMaxParticipants(2);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(3L);

            CustomException ex = assertThrows(CustomException.class,
                    () -> service.join(GUEST_ID, CONF_ID, null));
            assertEquals(400, ex.getCode());
            verify(valueOps).decrement("linkx:conference:active_count:" + CONF_ID);
        }

        @Test
        @DisplayName("已离开成员重新加入")
        void rejoinAfterLeave() {
            Conference conf = activeConference();
            ConferenceMember left = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            left.setLeftFlag(1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(left);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(2L);
            stubParticipants(hostMember(), memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1));
            stubAdmittedCount(2);

            service.join(MEMBER_ID, CONF_ID, null);
            verify(memberMapper).update(argThat(m -> m.getLeftFlag() == 0));
        }
    }

    @Nested
    @DisplayName("leave / end")
    class LeaveAndEnd {
        @Test
        @DisplayName("普通成员离开")
        void memberLeave() {
            Conference conf = activeConference();
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            stubParticipants(hostMember());
            stubAdmittedCount(1);

            service.leave(MEMBER_ID, CONF_ID);
            verify(memberMapper).update(argThat(m -> m.getLeftFlag() == 1));
            verify(callService).leaveConference(MEMBER_ID, CALL_ID);
            verify(valueOps).decrement("linkx:conference:active_count:" + CONF_ID);
        }

        @Test
        @DisplayName("主持人离开转让主持")
        void hostTransferOnLeave() {
            Conference conf = activeConference();
            ConferenceMember host = hostMember();
            ConferenceMember other = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            other.setJoinTime(new Date(System.currentTimeMillis() - 60_000));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(host, host, other, host, host);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(other), List.of(other), List.of(other));
            stubAdmittedCount(1);

            service.leave(HOST_ID, CONF_ID);
            verify(memberMapper, atLeast(2)).update(any(ConferenceMember.class));
        }

        @Test
        @DisplayName("非主持人不能结束")
        void endForbidden() {
            Conference conf = activeConference();
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1));
            assertThrows(CustomException.class, () -> service.end(MEMBER_ID, CONF_ID));
        }

        @Test
        @DisplayName("主持人结束会议")
        void endByHost() {
            Conference conf = activeConference();
            ConferenceMember host = hostMember();
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(host, member), List.of(host, member));
            when(sysUserMapper.selectOneById(HOST_ID)).thenReturn(
                    SysUser.builder().id(HOST_ID).nickname("Host").build());

            service.end(HOST_ID, CONF_ID);
            verify(conferenceMapper).update(argThat(c -> c.getStatus() == Conference.STATUS_ENDED));
            verify(redisTemplate).delete("linkx:conference:call:" + CONF_ID);
            verify(redisTemplate).delete("linkx:conference:active_count:" + CONF_ID);
            verify(chatService).postSystemMessage(eq(HOST_ID), eq(CONV_ID), anyString());
        }
    }

    @Nested
    @DisplayName("info / list")
    class InfoAndList {
        @Test
        @DisplayName("info 会议不存在")
        void infoNotFound() {
            when(conferenceMapper.selectOneById(999L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.info(HOST_ID, 999L));
        }

        @Test
        @DisplayName("info 成功")
        void infoOk() {
            Conference conf = activeConference();
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            stubParticipants(hostMember());

            ConferenceInfoVO vo = service.info(HOST_ID, CONF_ID);
            assertEquals("Team Sync", vo.getTitle());
            assertFalse(vo.getParticipants().isEmpty());
        }

        @Test
        @DisplayName("listActive 无成员关系")
        void listActiveEmpty() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            assertTrue(service.listActive(HOST_ID).isEmpty());
        }

        @Test
        @DisplayName("listActive 返回 ACTIVE")
        void listActiveOk() {
            ConferenceMember m = hostMember();
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(m));
            Conference conf = activeConference();
            when(conferenceMapper.selectListByIds(List.of(CONF_ID))).thenReturn(List.of(conf));
            when(valueOps.multiGet(anyList())).thenReturn(List.of(CALL_ID));
            stubParticipants(hostMember());

            List<ConferenceInfoVO> list = service.listActive(HOST_ID);
            assertEquals(1, list.size());
            assertEquals(CALL_ID, list.get(0).getCallId());
        }

        @Test
        @DisplayName("findActiveInConversation 无会议")
        void findActiveNull() {
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            assertNull(service.findActiveInConversation(HOST_ID, CONV_ID));
        }

        @Test
        @DisplayName("listHistory 已结束会议")
        void listHistory() {
            Conference ended = activeConference();
            ended.setStatus(Conference.STATUS_ENDED);
            ended.setEndTime(new Date());
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(ended));
            stubParticipants();

            List<ConferenceInfoVO> history = service.listHistory(HOST_ID, CONV_ID);
            assertEquals(1, history.size());
            assertNull(history.get(0).getCallId());
        }
    }

    @Nested
    @DisplayName("host controls")
    class HostControls {
        @Test
        @DisplayName("mute 联席不可操作主持人")
        void coHostCannotMuteHost() {
            ConferenceMember coHost = memberRow(MEMBER_ID, ConferenceMember.ROLE_CO_HOST, 1);
            ConferenceMember host = hostMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(coHost, host, coHost);
            assertThrows(CustomException.class,
                    () -> service.mute(MEMBER_ID, CONF_ID, HOST_ID, true));
        }

        @Test
        @DisplayName("mute 自己")
        void muteSelf() {
            ConferenceMember self = hostMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(self, self);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(self));

            service.mute(HOST_ID, CONF_ID, HOST_ID, true);
            verify(memberMapper).update(argThat(m -> m.getMuted() == 1));
        }

        @Test
        @DisplayName("setVideo 更新状态")
        void setVideo() {
            ConferenceMember self = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(self);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(self));

            service.setVideo(MEMBER_ID, CONF_ID, true);
            verify(memberMapper).update(argThat(m -> m.getVideoOff() == 1));
        }

        @Test
        @DisplayName("admitMember 成功")
        void admitMember() {
            Conference conf = activeConference();
            conf.setLobbyEnabled(1);
            ConferenceMember waiting = memberRow(GUEST_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(hostMember(), waiting);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(2L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(hostMember(), waiting));
            stubAdmittedCount(2);

            service.admitMember(HOST_ID, CONF_ID, GUEST_ID);
            verify(memberMapper).update(argThat(m -> m.getAdmitStatus() == 1));
            verify(callService).joinConference(GUEST_ID, CALL_ID);
        }

        @Test
        @DisplayName("admitMember 不在等候室")
        void admitNotWaiting() {
            Conference conf = activeConference();
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(hostMember(), null);
            assertThrows(CustomException.class, () -> service.admitMember(HOST_ID, CONF_ID, GUEST_ID));
        }

        @Test
        @DisplayName("admitMember 已满")
        void admitFull() {
            Conference conf = activeConference();
            conf.setMaxParticipants(1);
            ConferenceMember waiting = memberRow(GUEST_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(hostMember(), waiting);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(2L);

            assertThrows(CustomException.class, () -> service.admitMember(HOST_ID, CONF_ID, GUEST_ID));
            verify(valueOps).decrement("linkx:conference:active_count:" + CONF_ID);
        }

        @Test
        @DisplayName("setMemberRole 设为联席")
        void setCoHost() {
            ConferenceMember host = hostMember();
            ConferenceMember target = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, target);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host, target));

            service.setMemberRole(HOST_ID, CONF_ID, MEMBER_ID, ConferenceMember.ROLE_CO_HOST);
            verify(memberMapper).update(argThat(m ->
                    ConferenceMember.ROLE_CO_HOST.equals(m.getRole()) && m.getAdmitStatus() == 1));
        }

        @Test
        @DisplayName("setMemberRole 非法角色")
        void setRoleInvalid() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember());
            assertThrows(CustomException.class,
                    () -> service.setMemberRole(HOST_ID, CONF_ID, MEMBER_ID, "host"));
        }

        @Test
        @DisplayName("transferHost")
        void transferHost() {
            ConferenceMember host = hostMember();
            ConferenceMember target = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, target);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host, target));

            service.transferHost(HOST_ID, CONF_ID, MEMBER_ID);
            verify(memberMapper, times(2)).update(any(ConferenceMember.class));
        }

        @Test
        @DisplayName("removeMember")
        void removeMember() {
            Conference conf = activeConference();
            ConferenceMember host = hostMember();
            ConferenceMember target = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(host, target, target);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host));
            stubAdmittedCount(1);

            service.removeMember(HOST_ID, CONF_ID, MEMBER_ID);
            verify(pushService).pushToUser(eq(MEMBER_ID), eq("conference_remove"), anyMap());
        }

        @Test
        @DisplayName("raiseHand")
        void raiseHand() {
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(member));

            service.raiseHand(MEMBER_ID, CONF_ID, true);
            verify(memberMapper).selectListByQuery(any(QueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("signal")
    class Signal {
        @Test
        @DisplayName("未准入不能发信令")
        void notAdmitted() {
            ConferenceMember waiting = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(waiting);
            ConferenceSignalDTO dto = new ConferenceSignalDTO();
            dto.setConferenceId(CONF_ID);
            dto.setSignalType("offer");
            assertThrows(CustomException.class, () -> service.signal(MEMBER_ID, dto));
        }

        @Test
        @DisplayName("信令通道不存在")
        void missingCallId() {
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(valueOps.get("linkx:conference:call:" + CONF_ID)).thenReturn(null);

            ConferenceSignalDTO dto = new ConferenceSignalDTO();
            dto.setConferenceId(CONF_ID);
            dto.setSignalType("offer");
            assertThrows(CustomException.class, () -> service.signal(MEMBER_ID, dto));
        }

        @Test
        @DisplayName("信令转发成功")
        void signalOk() {
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);

            ConferenceSignalDTO dto = new ConferenceSignalDTO();
            dto.setConferenceId(CONF_ID);
            dto.setSignalType("ice-candidate");
            dto.setCandidate("c=1");
            dto.setTargetUserId(HOST_ID);

            service.signal(MEMBER_ID, dto);

            ArgumentCaptor<CallSignalDTO> captor = ArgumentCaptor.forClass(CallSignalDTO.class);
            verify(callService).signal(eq(MEMBER_ID), captor.capture());
            assertEquals(CALL_ID, captor.getValue().getCallId());
            assertEquals("ice-candidate", captor.getValue().getSignalType());
            verify(redisTemplate).expire(eq("linkx:conference:call:" + CONF_ID), any());
        }
    }

    @Nested
    @DisplayName("extended coverage")
    class ExtendedCoverage {
        @Test
        @DisplayName("create 语音通话场景")
        void createCallScene() {
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                Conference c = inv.getArgument(0);
                c.setId(CONF_ID);
                return 1;
            }).when(conferenceMapper).insert(any(Conference.class));
            stubParticipants(hostMember());

            ConferenceCreateDTO dto = createDto();
            dto.setScene(Conference.SCENE_CALL);
            dto.setType("voice");
            dto.setTitle(null);
            dto.setMaxParticipants(1);

            ConferenceInfoVO vo = service.create(HOST_ID, dto);
            assertEquals(Conference.SCENE_CALL, vo.getScene());
            assertEquals("语音通话", vo.getTitle());
            assertEquals(2, vo.getMaxParticipants());
        }

        @Test
        @DisplayName("已有会议时发起通话冲突")
        void meetingBlocksCall() {
            Conference meeting = activeConference();
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(meeting));

            ConferenceCreateDTO dto = createDto();
            dto.setScene(Conference.SCENE_CALL);
            CustomException ex = assertThrows(CustomException.class, () -> service.create(HOST_ID, dto));
            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains("会议"));
        }

        @Test
        @DisplayName("join 密码正确")
        void joinCorrectPassword() {
            Conference conf = activeConference();
            conf.setPassword(PasswordEncoderHolder.encode("secret"));
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(valueOps.increment("linkx:conference:active_count:" + CONF_ID)).thenReturn(2L);
            stubParticipants(hostMember(), memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1));
            stubAdmittedCount(2);

            ConferenceInfoVO vo = service.join(MEMBER_ID, CONF_ID, "secret");
            assertEquals(CONF_ID, vo.getId());
        }

        @Test
        @DisplayName("join 联席主持人在等候室直接准入")
        void coHostAdmittedInLobby() {
            Conference conf = activeConference();
            conf.setLobbyEnabled(1);
            ConferenceMember coHost = memberRow(MEMBER_ID, ConferenceMember.ROLE_CO_HOST, 0);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(coHost);
            stubParticipants(hostMember(), coHost);
            stubAdmittedCount(2);

            ConferenceInfoVO vo = service.join(MEMBER_ID, CONF_ID, null);
            assertFalse(vo.getWaitingAdmit());
            verify(callService).joinConference(MEMBER_ID, CALL_ID);
        }

        @Test
        @DisplayName("findActiveInConversation 返回 ACTIVE")
        void findActiveOk() {
            Conference conf = activeConference();
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(conf));
            stubParticipants(hostMember());

            ConferenceInfoVO vo = service.findActiveInConversation(HOST_ID, CONV_ID);
            assertNotNull(vo);
            assertEquals(CONF_ID, vo.getId());
        }

        @Test
        @DisplayName("listActive 过滤非 ACTIVE")
        void listActiveFiltersEnded() {
            ConferenceMember m = hostMember();
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(m));
            Conference ended = activeConference();
            ended.setStatus(Conference.STATUS_ENDED);
            when(conferenceMapper.selectListByIds(List.of(CONF_ID))).thenReturn(List.of(ended));

            assertTrue(service.listActive(HOST_ID).isEmpty());
        }

        @Test
        @DisplayName("end 创建者可结束")
        void endByCreator() {
            Conference conf = activeConference();
            conf.setCreatorId(MEMBER_ID);
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(member));
            when(sysUserMapper.selectOneById(MEMBER_ID)).thenReturn(
                    SysUser.builder().id(MEMBER_ID).nickname("Member").build());

            service.end(MEMBER_ID, CONF_ID);
            verify(conferenceMapper).update(argThat(c -> c.getStatus() == Conference.STATUS_ENDED));
        }

        @Test
        @DisplayName("mute 主持人禁言成员")
        void hostMutesMember() {
            ConferenceMember host = hostMember();
            ConferenceMember target = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, target);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host, target));

            service.mute(HOST_ID, CONF_ID, MEMBER_ID, true);
            verify(memberMapper).update(argThat(m -> m.getMuted() == 1));
        }

        @Test
        @DisplayName("联席不可操作其他联席")
        void coHostCannotOperateCoHost() {
            ConferenceMember coHost1 = memberRow(MEMBER_ID, ConferenceMember.ROLE_CO_HOST, 1);
            ConferenceMember coHost2 = memberRow(GUEST_ID, ConferenceMember.ROLE_CO_HOST, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(coHost1, coHost2);
            assertThrows(CustomException.class, () -> service.mute(MEMBER_ID, CONF_ID, GUEST_ID, true));
        }

        @Test
        @DisplayName("setMemberRole 不能改自己")
        void setRoleSelfForbidden() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember());
            assertThrows(CustomException.class,
                    () -> service.setMemberRole(HOST_ID, CONF_ID, HOST_ID, ConferenceMember.ROLE_CO_HOST));
        }

        @Test
        @DisplayName("setMemberRole 目标仍是主持人")
        void setRoleTargetHost() {
            ConferenceMember host = hostMember();
            ConferenceMember otherHost = memberRow(MEMBER_ID, ConferenceMember.ROLE_HOST, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, otherHost);
            assertThrows(CustomException.class,
                    () -> service.setMemberRole(HOST_ID, CONF_ID, MEMBER_ID, ConferenceMember.ROLE_MEMBER));
        }

        @Test
        @DisplayName("setMemberRole 等候室自动准入递增计数")
        void setRoleFromWaiting() {
            ConferenceMember host = hostMember();
            ConferenceMember waiting = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, waiting);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host, waiting));

            service.setMemberRole(HOST_ID, CONF_ID, MEMBER_ID, ConferenceMember.ROLE_CO_HOST);
            verify(valueOps).increment("linkx:conference:active_count:" + CONF_ID);
        }

        @Test
        @DisplayName("transferHost 等候室新主持自动入会")
        void transferToWaitingGuest() {
            Conference conf = activeConference();
            ConferenceMember host = hostMember();
            ConferenceMember waiting = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(host, waiting);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(host, waiting));

            service.transferHost(HOST_ID, CONF_ID, MEMBER_ID);
            verify(callService).joinConference(MEMBER_ID, CALL_ID);
            verify(valueOps).increment("linkx:conference:active_count:" + CONF_ID);
        }

        @Test
        @DisplayName("admitMember 已准入直接返回")
        void admitAlreadyAdmitted() {
            Conference conf = activeConference();
            ConferenceMember admitted = memberRow(GUEST_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember(), admitted);

            service.admitMember(HOST_ID, CONF_ID, GUEST_ID);
            verify(memberMapper, never()).update(admitted);
        }

        @Test
        @DisplayName("leave 无 callId 仍更新成员")
        void leaveWithoutCallId() {
            Conference conf = activeConference();
            ConferenceMember member = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 1);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(conf);
            when(valueOps.get("linkx:conference:call:" + CONF_ID)).thenReturn(null);
            stubParticipants(hostMember());
            stubAdmittedCount(1);

            service.leave(MEMBER_ID, CONF_ID);
            verify(callService, never()).leaveConference(anyLong(), anyString());
            verify(memberMapper).update(argThat(m -> m.getLeftFlag() == 1));
        }

        @Test
        @DisplayName("ensureCallChannel 过期后重建")
        void recreateExpiredCallChannel() {
            Conference existing = activeConference();
            when(conferenceMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));
            when(conferenceMapper.selectOneById(CONF_ID)).thenReturn(existing);
            when(valueOps.get("linkx:conference:call:" + CONF_ID)).thenReturn("old-call");
            when(hashOps.entries("linkx:call:old-call")).thenReturn(Map.of("status", "ended"));
            when(callService.createConference(anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyBoolean(), anyString()))
                    .thenReturn("new-call");
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(hostMember());
            stubParticipants(hostMember());
            when(valueOps.increment(anyString())).thenReturn(1L);

            service.create(HOST_ID, createDto());
            verify(valueOps, atLeastOnce()).set(eq("linkx:conference:call:" + CONF_ID), eq("new-call"), any());
        }

        @Test
        @DisplayName("raiseHand 未准入拒绝")
        void raiseHandNotAdmitted() {
            ConferenceMember waiting = memberRow(MEMBER_ID, ConferenceMember.ROLE_MEMBER, 0);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(waiting);
            assertThrows(CustomException.class, () -> service.raiseHand(MEMBER_ID, CONF_ID, true));
        }

        @Test
        @DisplayName("removeMember 联席不可踢主持人")
        void coHostCannotRemoveHost() {
            ConferenceMember coHost = memberRow(MEMBER_ID, ConferenceMember.ROLE_CO_HOST, 1);
            ConferenceMember host = hostMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(coHost, host, coHost);
            assertThrows(CustomException.class, () -> service.removeMember(MEMBER_ID, CONF_ID, HOST_ID));
        }
    }
}
