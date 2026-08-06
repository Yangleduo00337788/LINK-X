package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.InviteGroupDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupInvitationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.GroupInvitation;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupInvitationServiceImpl 群邀请")
class GroupInvitationServiceImplTest {

    private static final long INVITER_ID = 1L;
    private static final long INVITEE_ID = 2L;
    private static final long GROUP_ID = 100L;
    private static final long INVITATION_ID = 500L;

    @Mock GroupInvitationMapper invitationMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock SysUserMapper userMapper;
    @Mock ImMessagePushService imPushService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ChatService chatService;

    private GroupInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(chatService.postSystemMessage(any(), anyLong(), anyString()))
                .thenReturn(MessageVO.builder().id(1L).type("system").content("tip").build());
        service = new GroupInvitationServiceImpl(
                invitationMapper, conversationMapper, memberMapper, userMapper,
                imPushService, mediaUrlService, chatService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private ImConversation group(String invitePolicy) {
        return ImConversation.builder()
                .id(GROUP_ID)
                .type(ImConversation.TYPE_GROUP)
                .name("Dev Group")
                .ownerId(INVITER_ID)
                .avatar("g.png")
                .invitePolicy(invitePolicy)
                .build();
    }

    private ImConversationMember memberRow(long userId, String role) {
        return ImConversationMember.builder()
                .conversationId(GROUP_ID)
                .userId(userId)
                .role(role)
                .muted(0)
                .deleted(0)
                .createTime(new Date())
                .build();
    }

    private GroupInvitation pendingInvitation() {
        return GroupInvitation.builder()
                .id(INVITATION_ID)
                .conversationId(GROUP_ID)
                .inviterUserId(INVITER_ID)
                .inviteeUserId(INVITEE_ID)
                .message("join us")
                .status(GroupInvitation.STATUS_PENDING)
                .createTime(new Date())
                .build();
    }

    private InviteGroupDTO inviteDto() {
        InviteGroupDTO dto = new InviteGroupDTO();
        dto.setInviteeUserId(INVITEE_ID);
        dto.setMessage("hello");
        return dto;
    }

    private void stubInviterMember() {
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group(null));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER));
    }

    @Nested
    @DisplayName("invite")
    class InviteTests {
        @Test
        @DisplayName("成功创建邀请")
        void success() {
            stubInviterMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER), null);
            when(invitationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> {
                GroupInvitation gi = inv.getArgument(0);
                gi.setId(INVITATION_ID);
                return 1;
            }).when(invitationMapper).insert(any(GroupInvitation.class));

            GroupInvitationVO vo = service.invite(INVITER_ID, GROUP_ID, inviteDto());
            assertEquals(INVITATION_ID, vo.getId());
            assertEquals("Dev Group", vo.getGroupName());
            verify(imPushService).pushToUser(eq(INVITEE_ID), eq("notification_refresh"), anyMap());
        }

        @Test
        @DisplayName("被邀请人已是成员")
        void inviteeAlreadyMember() {
            stubInviterMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER),
                            memberRow(INVITEE_ID, ImConversationMember.ROLE_MEMBER));
            assertThrows(CustomException.class, () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("已有 pending 刷新留言")
        void refreshPending() {
            stubInviterMember();
            GroupInvitation pending = pendingInvitation();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER), null);
            when(invitationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(pending);

            InviteGroupDTO dto = inviteDto();
            dto.setMessage("updated msg");
            GroupInvitationVO vo = service.invite(INVITER_ID, GROUP_ID, dto);
            assertEquals(INVITATION_ID, vo.getId());
            verify(invitationMapper).update(pending);
            verify(invitationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("群不存在")
        void groupMissing() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("非群聊")
        void notGroup() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(
                    ImConversation.builder().id(GROUP_ID).type(ImConversation.TYPE_PRIVATE).build());
            assertThrows(CustomException.class, () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("非成员无权邀请")
        void notMember() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group(null));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertThrows(CustomException.class, () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("ownerApprove 普通成员拒绝")
        void ownerApproveMemberForbidden() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group("ownerApprove"));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_MEMBER));
            assertThrows(CustomException.class, () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("ownerApprove 管理员可邀请")
        void ownerApproveAdminOk() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group("ownerApprove"));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_ADMIN),
                            memberRow(INVITER_ID, ImConversationMember.ROLE_ADMIN),
                            null);
            when(invitationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> {
                GroupInvitation gi = inv.getArgument(0);
                gi.setId(INVITATION_ID);
                return 1;
            }).when(invitationMapper).insert(any(GroupInvitation.class));

            assertNotNull(service.invite(INVITER_ID, GROUP_ID, inviteDto()));
        }

        @Test
        @DisplayName("未知邀请策略 fail-safe")
        void unknownPolicy() {
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group("customPolicy"));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER));
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.invite(INVITER_ID, GROUP_ID, inviteDto()));
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("并发插入冲突后重查")
        void concurrentInsert() {
            stubInviterMember();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER), null);
            when(invitationMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(null, pendingInvitation());
            doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                    .when(invitationMapper).insert(any(GroupInvitation.class));

            GroupInvitationVO vo = service.invite(INVITER_ID, GROUP_ID, inviteDto());
            assertEquals(INVITATION_ID, vo.getId());
        }
    }

    @Nested
    @DisplayName("listMyInvitations")
    class ListTests {
        @Test
        @DisplayName("空列表")
        void empty() {
            when(invitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            assertTrue(service.listMyInvitations(INVITEE_ID).isEmpty());
        }

        @Test
        @DisplayName("返回邀请人与群信息")
        void withDetails() {
            GroupInvitation inv = pendingInvitation();
            when(invitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(inv));
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(group(null)));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(INVITER_ID).nickname("Inviter").avatar("av1").build()));

            List<GroupInvitationVO> list = service.listMyInvitations(INVITEE_ID);
            assertEquals(1, list.size());
            assertEquals("Inviter", list.get(0).getInviterNickname());
            assertEquals("https://cdn/av1", list.get(0).getInviterAvatar());
        }
    }

    @Nested
    @DisplayName("accept / reject")
    class AcceptRejectTests {
        @BeforeEach
        void initTx() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @Test
        @DisplayName("接受邀请并写入成员")
        void acceptSuccess() {
            GroupInvitation inv = pendingInvitation();
            when(invitationMapper.selectOneById(INVITATION_ID)).thenReturn(inv);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group(null));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(invitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectOneById(INVITEE_ID)).thenReturn(
                    SysUser.builder().id(INVITEE_ID).nickname("Guest").avatar("av2").username("guest").build());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(memberRow(INVITER_ID, ImConversationMember.ROLE_OWNER)));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(INVITER_ID).nickname("Owner").avatar("av1").username("owner").build()));

            GroupConversationVO vo = service.accept(INVITEE_ID, INVITATION_ID);
            assertEquals(GROUP_ID, vo.getId());
            verify(memberMapper).insert(any(ImConversationMember.class));
            verify(invitationMapper).update(argThat(i -> i.getStatus() == GroupInvitation.STATUS_ACCEPTED));

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(imPushService).pushToUser(eq(INVITEE_ID), eq("group_added"), anyMap());
            verify(chatService).postSystemMessage(any(), eq(GROUP_ID), contains("加入了群聊"));
        }

        @Test
        @DisplayName("接受邀请恢复软删成员")
        void acceptRestoreSoftDeleted() {
            GroupInvitation inv = pendingInvitation();
            ImConversationMember softDeleted = memberRow(INVITEE_ID, ImConversationMember.ROLE_MEMBER);
            softDeleted.setDeleted(1);

            when(invitationMapper.selectOneById(INVITATION_ID)).thenReturn(inv);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(group(null));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(null, softDeleted);
            when(invitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectOneById(INVITEE_ID)).thenReturn(
                    SysUser.builder().id(INVITEE_ID).nickname("Guest").avatar("av2").build());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.accept(INVITEE_ID, INVITATION_ID);
            verify(memberMapper).update(argThat(m -> m.getDeleted() == 0));
            verify(memberMapper, never()).insert(any());
        }

        @Test
        @DisplayName("邀请不存在")
        void acceptNotFound() {
            when(invitationMapper.selectOneById(999L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.accept(INVITEE_ID, 999L));
        }

        @Test
        @DisplayName("无权操作他人邀请")
        void acceptForbidden() {
            GroupInvitation inv = pendingInvitation();
            when(invitationMapper.selectOneById(INVITATION_ID)).thenReturn(inv);
            assertThrows(CustomException.class, () -> service.accept(99L, INVITATION_ID));
        }

        @Test
        @DisplayName("邀请已处理")
        void acceptAlreadyHandled() {
            GroupInvitation inv = pendingInvitation();
            inv.setStatus(GroupInvitation.STATUS_ACCEPTED);
            when(invitationMapper.selectOneById(INVITATION_ID)).thenReturn(inv);
            assertThrows(CustomException.class, () -> service.accept(INVITEE_ID, INVITATION_ID));
        }

        @Test
        @DisplayName("拒绝邀请")
        void rejectSuccess() {
            GroupInvitation inv = pendingInvitation();
            when(invitationMapper.selectOneById(INVITATION_ID)).thenReturn(inv);
            when(invitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

            service.reject(INVITEE_ID, INVITATION_ID);
            verify(invitationMapper).update(argThat(i -> i.getStatus() == GroupInvitation.STATUS_REJECTED));
        }

        @Test
        @DisplayName("clearProcessedInvitations 仅删除已处理邀请")
        void clearProcessed() {
            when(invitationMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(2);
            assertEquals(2, service.clearProcessedInvitations(INVITEE_ID));
            verify(invitationMapper).deleteByQuery(any(QueryWrapper.class));
        }
    }
}
