package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.MuteAllDTO;
import com.linkx.server.controller.dto.MuteMemberDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupJoinRequestVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.MessageNotification;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.GroupAnnouncementMapper;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.GroupInvitationMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.mybatisflex.core.query.QueryConditionBuilder;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupServiceImpl 单元测试")
class GroupServiceImplTest {

    private static final long OWNER_ID = 1L;
    private static final long ADMIN_ID = 2L;
    private static final long MEMBER_ID = 3L;
    private static final long GROUP_ID = 100L;

    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysUserRelationMapper relationMapper;
    @Mock MediaUrlService mediaUrlService;
    @Mock ChatService chatService;
    @Mock ImMessagePushService imPushService;
    @Mock MessageNotificationService notificationService;
    @Mock MessageNotificationMapper notificationMapper;
    @Mock GroupAnnouncementMapper groupAnnouncementMapper;
    @Mock GroupAssetMapper groupAssetMapper;
    @Mock GroupInvitationMapper groupInvitationMapper;
    @Mock ImMessageMapper messageMapper;
    @Mock FileStorageService fileStorageService;

    private GroupServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(chatService.postSystemMessage(any(), anyLong(), anyString())).thenReturn(
                MessageVO.builder().id(1L).type("system").content("tip").build()
        );
        service = new GroupServiceImpl(
                conversationMapper, memberMapper, sysUserMapper, relationMapper, mediaUrlService,
                chatService, imPushService, notificationService, notificationMapper,
                groupAnnouncementMapper, groupAssetMapper, groupInvitationMapper, messageMapper, fileStorageService
        );
    }

    private ImConversation group(long id, long ownerId) {
        return ImConversation.builder()
                .id(id)
                .type(ImConversation.TYPE_GROUP)
                .name("Test Group")
                .ownerId(ownerId)
                .muteAll(0)
                .deleted(0)
                .build();
    }

    private ImConversationMember memberRow(long userId, String role) {
        return ImConversationMember.builder()
                .id(userId + 10_000)
                .conversationId(GROUP_ID)
                .userId(userId)
                .role(role)
                .muted(0)
                .deleted(0)
                .createTime(new Date())
                .build();
    }

    private SysUser user(long id, String nick) {
        return SysUser.builder().id(id).username("u" + id).nickname(nick).avatar("av" + id).build();
    }

    private void stubOwnerMember() {
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateChain stubConversationUpdateChain() {
        UpdateChain chain = mock(UpdateChain.class);
        QueryConditionBuilder condition = mock(QueryConditionBuilder.class);
        lenient().when(chain.set(any(com.mybatisflex.core.util.LambdaGetter.class), any())).thenReturn(chain);
        lenient().when(chain.where(any(com.mybatisflex.core.util.LambdaGetter.class))).thenReturn(condition);
        lenient().doReturn(chain).when(condition).eq(any());
        lenient().doReturn(chain).when(condition).eq(anyLong());
        lenient().when(chain.update()).thenReturn(true);
        return chain;
    }

    private void withConversationUpdateChain(Runnable action) {
        UpdateChain chain = stubConversationUpdateChain();
        try (MockedStatic<UpdateChain> updateChain = mockStatic(UpdateChain.class)) {
            updateChain.when(() -> UpdateChain.of(ImConversation.class)).thenReturn(chain);
            action.run();
        }
    }

    @Nested
    @DisplayName("createGroup")
    class CreateGroup {
        @Test
        @DisplayName("创建者不存在")
        void creatorMissing() {
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(null);
            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("G");
            dto.setMemberIds(List.of(MEMBER_ID));
            assertThrows(CustomException.class, () -> service.createGroup(OWNER_ID, dto));
        }

        @Test
        @DisplayName("成员列表为空")
        void emptyMembers() {
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("G");
            dto.setMemberIds(new ArrayList<>());
            CustomException ex = assertThrows(CustomException.class, () -> service.createGroup(OWNER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("只能邀请好友")
        void notFriends() {
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(MEMBER_ID, "M")));
            when(relationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("G");
            dto.setMemberIds(List.of(MEMBER_ID));
            assertThrows(CustomException.class, () -> service.createGroup(OWNER_ID, dto));
        }

        @Test
        @DisplayName("创建成功")
        void success() {
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(MEMBER_ID, "M")));
            when(relationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(conversationMapper.insert(any(ImConversation.class))).thenAnswer(inv -> {
                ImConversation g = inv.getArgument(0);
                g.setId(GROUP_ID);
                return 1;
            });
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER),
                    memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER)
            ));

            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("My Group");
            dto.setMemberIds(List.of(MEMBER_ID));
            GroupConversationVO vo = service.createGroup(OWNER_ID, dto);
            assertEquals(GROUP_ID, vo.getId());
            verify(memberMapper).insertBatch(anyList());
        }
    }

    @Nested
    @DisplayName("listGroups / getGroupInfo")
    class ListAndInfo {
        @Test
        @DisplayName("listGroups 空")
        void empty() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            assertTrue(service.listGroups(OWNER_ID).isEmpty());
        }

        @Test
        @DisplayName("listGroups 返回群列表")
        void withGroups() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)))
                    .thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));
            ImConversation g = group(GROUP_ID, OWNER_ID);
            g.setLastMessageTime(new Date());
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(new ArrayList<>(List.of(g)));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(OWNER_ID, "Owner")));

            List<ConversationVO> list = service.listGroups(OWNER_ID);
            assertEquals(1, list.size());
            assertEquals("Test Group", list.get(0).getName());
        }

        @Test
        @DisplayName("getGroupInfo 非群或不存在")
        void notFound() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.getGroupInfo(OWNER_ID, GROUP_ID));

            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(
                    ImConversation.builder().id(GROUP_ID).type(ImConversation.TYPE_PRIVATE).build()
            );
            assertThrows(CustomException.class, () -> service.getGroupInfo(OWNER_ID, GROUP_ID));
        }
    }

    @Nested
    @DisplayName("updateGroup / 权限")
    class UpdateAndAuth {
        @Test
        @DisplayName("非管理员不能改公告")
        void memberCannotUpdate() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER)
            );

            UpdateGroupDTO dto = new UpdateGroupDTO();
            dto.setAnnouncement("new");
            assertThrows(CustomException.class, () -> service.updateGroup(MEMBER_ID, GROUP_ID, dto));
        }

        @Test
        @DisplayName("仅群主可改名")
        void renameOwnerOnly() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN)
            );

            UpdateGroupDTO dto = new UpdateGroupDTO();
            dto.setName("New Name");
            assertThrows(CustomException.class, () -> service.updateGroup(ADMIN_ID, GROUP_ID, dto));
        }

        @Test
        @DisplayName("空更新直接返回")
        void noopUpdate() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN)
            );
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN)));

            UpdateGroupDTO dto = new UpdateGroupDTO();
            GroupConversationVO vo = service.updateGroup(ADMIN_ID, GROUP_ID, dto);
            assertEquals(GROUP_ID, vo.getId());
            verify(conversationMapper, never()).update(any());
        }
    }

    @Nested
    @DisplayName("成员管理")
    class Members {
        @Test
        @DisplayName("listMembers")
        void listMembers() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER),
                    memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER)
            ));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    user(OWNER_ID, "Owner"), user(MEMBER_ID, "Member")
            ));

            List<GroupMemberVO> members = service.listMembers(OWNER_ID, GROUP_ID);
            assertEquals(2, members.size());
        }

        @Test
        @DisplayName("removeMember 不能移除群主")
        void cannotRemoveOwner() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));

            assertThrows(CustomException.class, () -> service.removeMember(ADMIN_ID, GROUP_ID, OWNER_ID));
        }

        @Test
        @DisplayName("管理员不能移除其他管理员")
        void adminCannotRemoveAdmin() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                    .thenReturn(memberRow(4L, ImConversationMember.ROLE_ADMIN));

            assertThrows(CustomException.class, () -> service.removeMember(ADMIN_ID, GROUP_ID, 4L));
        }

        @Test
        @DisplayName("quitGroup 群主不能退群")
        void ownerCannotQuit() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            CustomException ex = assertThrows(CustomException.class, () -> service.quitGroup(OWNER_ID, GROUP_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("dissolveGroup 仅群主")
        void dissolveOwnerOnly() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN));
            assertThrows(CustomException.class, () -> service.dissolveGroup(ADMIN_ID, GROUP_ID));
        }

        @Test
        @DisplayName("dissolveGroup 成功清理")
        void dissolveOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));
            when(groupAssetMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.dissolveGroup(OWNER_ID, GROUP_ID);
            verify(conversationMapper).deleteById(GROUP_ID);
            verify(memberMapper).deleteByQuery(any(QueryWrapper.class));
        }

        @Test
        @DisplayName("dissolveGroup 成功清理资产")
        void dissolveWithAssets() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));
            when(groupAssetMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    GroupAsset.builder().conversationId(GROUP_ID).fileKey("2026/group/a.jpg").build(),
                    GroupAsset.builder().conversationId(GROUP_ID).fileKey("  ").build()
            ));

            service.dissolveGroup(OWNER_ID, GROUP_ID);
            verify(fileStorageService).deleteFile("2026/group/a.jpg");
            verify(conversationMapper).deleteById(GROUP_ID);
        }
    }

    @Nested
    @DisplayName("角色与禁言")
    class RoleAndMute {
        @Test
        @DisplayName("updateMemberRole 非法角色")
        void badRole() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));

            assertThrows(CustomException.class,
                    () -> service.updateMemberRole(OWNER_ID, GROUP_ID, MEMBER_ID, ImConversationMember.ROLE_OWNER));
            assertThrows(CustomException.class,
                    () -> service.updateMemberRole(OWNER_ID, GROUP_ID, OWNER_ID, ImConversationMember.ROLE_ADMIN));
        }

        @Test
        @DisplayName("updateMemberMute 不能禁言群主/自己")
        void muteGuards() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN));

            MuteMemberDTO dto = new MuteMemberDTO();
            dto.setMuted(true);
            assertThrows(CustomException.class, () -> service.updateMemberMute(ADMIN_ID, GROUP_ID, OWNER_ID, dto));
            assertThrows(CustomException.class, () -> service.updateMemberMute(ADMIN_ID, GROUP_ID, ADMIN_ID, dto));
        }

        @Test
        @DisplayName("updateMuteAll 参数校验")
        void muteAllValidation() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN));

            assertThrows(CustomException.class, () -> service.updateMuteAll(ADMIN_ID, GROUP_ID, null));

            MuteAllDTO badTime = new MuteAllDTO();
            badTime.setStartTime(2000L);
            badTime.setEndTime(1000L);
            assertThrows(CustomException.class, () -> service.updateMuteAll(ADMIN_ID, GROUP_ID, badTime));

            MuteAllDTO empty = new MuteAllDTO();
            assertThrows(CustomException.class, () -> service.updateMuteAll(ADMIN_ID, GROUP_ID, empty));
        }

        @Test
        @DisplayName("isMuteAllActive / isMemberMuteActive")
        void staticMuteHelpers() {
            Date now = new Date();
            ImConversation timed = ImConversation.builder()
                    .muteAll(0)
                    .muteAllStart(new Date(now.getTime() - 1000))
                    .muteAllEnd(new Date(now.getTime() + 60_000))
                    .build();
            assertTrue(GroupServiceImpl.isMuteAllActive(timed, now));

            ImConversationMember muted = ImConversationMember.builder().muted(1).muteUntil(new Date(now.getTime() + 60_000)).build();
            assertTrue(GroupServiceImpl.isMemberMuteActive(muted, now));
            assertFalse(GroupServiceImpl.isMemberMuteActive(ImConversationMember.builder().muted(0).build(), now));
        }
    }

    @Nested
    @DisplayName("批量与策略")
    class BatchAndPolicy {
        @Test
        @DisplayName("batchRemoveMembers 空列表")
        void batchRemoveEmpty() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            assertThrows(CustomException.class, () -> service.batchRemoveMembers(OWNER_ID, GROUP_ID, List.of()));
        }

        @Test
        @DisplayName("setInvitePolicy 非法值")
        void badInvitePolicy() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            assertThrows(CustomException.class, () -> service.setInvitePolicy(OWNER_ID, GROUP_ID, "bad"));
        }

        @Test
        @DisplayName("updateMyRemark 截断")
        void myRemark() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));
            String longRemark = "x".repeat(80);
            String saved = service.updateMyRemark(MEMBER_ID, GROUP_ID, longRemark);
            assertEquals(64, saved.length());
        }
    }

    @Test
    @DisplayName("addMembers 超过容量")
    void addMembersOverCapacity() {
        ImConversation g = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN));
        when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(500L);

        AddGroupMembersDTO dto = new AddGroupMembersDTO();
        dto.setMemberIds(List.of(99L));
        assertThrows(CustomException.class, () -> service.addMembers(ADMIN_ID, GROUP_ID, dto));
    }

    @Test
    @DisplayName("transferOwner 新群主不在群中")
    void transferOwnerNotInGroup() {
        ImConversation g = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                .thenReturn(null);

        assertThrows(CustomException.class, () -> service.transferOwner(OWNER_ID, GROUP_ID, 999L));
    }

    @Test
    @DisplayName("adminDissolveGroup 非群")
    void adminDissolveNotGroup() {
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(
                ImConversation.builder().id(GROUP_ID).type(ImConversation.TYPE_PRIVATE).build()
        );
        assertThrows(CustomException.class, () -> service.adminDissolveGroup(GROUP_ID, 99L));
    }

    @Test
    @DisplayName("handleJoinRequest 已是成员")
    void joinRequestAlreadyMember() {
        ImConversation g = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                .thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));

        assertThrows(CustomException.class,
                () -> service.handleJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID, true));
    }

    @Test
    @DisplayName("requestJoin 已是活跃成员")
    void requestJoinAlreadyIn() {
        ImConversation g = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER)
        );
        assertThrows(CustomException.class, () -> service.requestJoin(MEMBER_ID, GROUP_ID, null));
    }

    @Test
    @DisplayName("batchMuteMembers 空列表")
    void batchMuteEmpty() {
        ImConversation g = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
        assertThrows(CustomException.class,
                () -> service.batchMuteMembers(OWNER_ID, GROUP_ID, List.of(), true));
    }

    @Nested
    @DisplayName("成功路径")
    class SuccessPaths {
        @Test
        @DisplayName("getGroupInfo 成功")
        void getGroupInfoOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));

            GroupConversationVO vo = service.getGroupInfo(OWNER_ID, GROUP_ID);
            assertEquals(GROUP_ID, vo.getId());
            assertEquals("Test Group", vo.getName());
        }

        @Test
        @DisplayName("updateGroup 改名成功")
        void renameOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));

            UpdateGroupDTO dto = new UpdateGroupDTO();
            dto.setName("Renamed");
            GroupConversationVO vo = service.updateGroup(OWNER_ID, GROUP_ID, dto);
            assertEquals("Renamed", vo.getName());
            verify(conversationMapper).update(g);
        }

        @Test
        @DisplayName("removeMember / quitGroup 成功")
        void removeAndQuit() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));

            service.removeMember(OWNER_ID, GROUP_ID, MEMBER_ID);
            verify(memberMapper).deleteById(anyLong());

            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));
            service.quitGroup(MEMBER_ID, GROUP_ID);
            verify(memberMapper, times(2)).deleteById(anyLong());
        }

        @Test
        @DisplayName("transferOwner 成功")
        void transferOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U" + inv.getArgument(0)));

            service.transferOwner(OWNER_ID, GROUP_ID, ADMIN_ID);
            assertEquals(ADMIN_ID, g.getOwnerId());
            verify(conversationMapper).update(g);
        }

        @Test
        @DisplayName("updateMemberRole 设为管理员")
        void promoteAdmin() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            ImConversationMember target = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(target);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)));
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U" + inv.getArgument(0)));

            service.updateMemberRole(OWNER_ID, GROUP_ID, MEMBER_ID, ImConversationMember.ROLE_ADMIN);
            assertEquals(ImConversationMember.ROLE_ADMIN, target.getRole());
        }

        @Test
        @DisplayName("setJoinApproval / setInvitePolicy")
        void policies() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));

            service.setJoinApproval(OWNER_ID, GROUP_ID, true);
            assertEquals(1, g.getJoinApproval());
            service.setInvitePolicy(OWNER_ID, GROUP_ID, "ownerApprove");
            assertEquals("ownerApprove", g.getInvitePolicy());
            verify(conversationMapper, times(2)).update(g);
        }

        @Test
        @DisplayName("公告已读")
        void announcementRead() {
            ImConversationMember me = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(me);
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);

            service.markAnnouncementRead(MEMBER_ID, GROUP_ID);
            assertTrue(me.getAnnouncementRead());
            assertEquals(3L, service.getAnnouncementReadCount(MEMBER_ID, GROUP_ID));
        }

        @Test
        @DisplayName("createGroup 部分成员不存在")
        void partialMembersMissing() {
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("G");
            dto.setMemberIds(List.of(MEMBER_ID));
            assertThrows(CustomException.class, () -> service.createGroup(OWNER_ID, dto));
        }

        @Test
        @DisplayName("addMembers 成功拉人")
        void addMembersOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)) // assertGroupAdmin / member
                    .thenReturn(null); // ensureActiveMembership: not active
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(groupInvitationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U" + inv.getArgument(0)));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(99L, "New")));
            when(memberMapper.insert(any(ImConversationMember.class))).thenAnswer(inv -> {
                ImConversationMember m = inv.getArgument(0);
                m.setId(1000L);
                m.setCreateTime(new Date());
                return 1;
            });

            AddGroupMembersDTO dto = new AddGroupMembersDTO();
            dto.setMemberIds(List.of(99L));
            List<GroupMemberVO> added = service.addMembers(OWNER_ID, GROUP_ID, dto);
            assertEquals(1, added.size());
            assertEquals(99L, added.get(0).getUserId());
            verify(memberMapper).insert(any(ImConversationMember.class));
        }

        @Test
        @DisplayName("requestJoin 无需审批直接加入")
        void requestJoinDirect() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            g.setJoinApproval(0);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(sysUserMapper.selectOneById(MEMBER_ID)).thenReturn(user(MEMBER_ID, "Member"));
            when(memberMapper.insert(any(ImConversationMember.class))).thenAnswer(inv -> {
                ImConversationMember m = inv.getArgument(0);
                m.setId(1001L);
                return 1;
            });

            service.requestJoin(MEMBER_ID, GROUP_ID, null);
            verify(memberMapper).insert(any(ImConversationMember.class));
        }

        @Test
        @DisplayName("requestJoin 需审批通知管理员")
        void requestJoinNeedsApproval() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            g.setJoinApproval(1);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER)
            ));
            when(notificationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysUserMapper.selectOneById(MEMBER_ID)).thenReturn(user(MEMBER_ID, "Member"));

            service.requestJoin(MEMBER_ID, GROUP_ID, "please");
            verify(notificationService).create(eq(OWNER_ID), eq(MEMBER_ID), anyString(), any(),
                    eq("group_join_request"), eq(GROUP_ID), eq("please"));
        }

        @Test
        @DisplayName("handleJoinRequest 拒绝")
        void handleJoinReject() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(null);
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U"));
            when(notificationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.handleJoinRequest(OWNER_ID, GROUP_ID, MEMBER_ID, false);
            verify(imPushService).pushToUser(eq(MEMBER_ID), eq("notification_refresh"), anyMap());
        }

        @Test
        @DisplayName("listJoinRequests 返回待审批")
        void listJoinRequestsOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            MessageNotification n = MessageNotification.builder()
                    .id(5L).senderId(MEMBER_ID).senderName("Member").senderAvatar("av")
                    .content("hi").createTime(new Date()).build();
            when(notificationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(n));

            List<GroupJoinRequestVO> list = service.listJoinRequests(OWNER_ID, GROUP_ID);
            assertEquals(1, list.size());
            assertEquals(MEMBER_ID, list.get(0).getApplicantId());
        }

        @Test
        @DisplayName("adminDissolveGroup 成功")
        void adminDissolveOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER),
                    memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER)
            ));
            when(groupAssetMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.adminDissolveGroup(GROUP_ID, 99L);
            verify(conversationMapper).deleteById(GROUP_ID);
            verify(memberMapper).deleteByQuery(any(QueryWrapper.class));
            verify(imPushService, times(2)).pushToUser(anyLong(), eq("group_dissolved"), anyMap());
        }

        @Test
        @DisplayName("handleJoinRequest 批准加入")
        void handleJoinApprove() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(null)
                    .thenReturn(null)
                    .thenReturn(null);
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(memberMapper.insert(any(ImConversationMember.class))).thenAnswer(inv -> {
                ImConversationMember m = inv.getArgument(0);
                m.setId(2001L);
                return 1;
            });
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U" + inv.getArgument(0)));
            when(notificationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            service.handleJoinRequest(OWNER_ID, GROUP_ID, MEMBER_ID, true);
            verify(memberMapper).insert(any(ImConversationMember.class));
            verify(imPushService).pushToUser(eq(MEMBER_ID), eq("notification_refresh"), anyMap());
        }

        @Test
        @DisplayName("applyMuteSchedules 到期成员解禁")
        void applyMuteSchedulesUnmutes() {
            Date past = new Date(System.currentTimeMillis() - 60_000);
            ImConversationMember expired = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            expired.setMuted(1);
            expired.setMuteUntil(past);

            when(conversationMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of())
                    .thenReturn(List.of());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(expired));

            service.applyMuteSchedules();

            assertEquals(0, expired.getMuted());
            assertNull(expired.getMuteUntil());
            verify(memberMapper).update(expired);
        }

        @Test
        @DisplayName("batchRemoveMembers 成功")
        void batchRemoveOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            ImConversationMember target = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(target);
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));

            service.batchRemoveMembers(OWNER_ID, GROUP_ID, List.of(MEMBER_ID));
            verify(memberMapper).deleteById(target.getId());
        }

        @Test
        @DisplayName("batchMuteMembers 成功")
        void batchMuteOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            ImConversationMember target = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(target);
            when(sysUserMapper.selectOneById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));

            service.batchMuteMembers(OWNER_ID, GROUP_ID, List.of(MEMBER_ID), true);
            assertEquals(1, target.getMuted());
            verify(memberMapper).update(target);
        }

        @Test
        @DisplayName("getAnnouncementReadCount 统计")
        void announcementReadCount() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);

            assertEquals(5L, service.getAnnouncementReadCount(MEMBER_ID, GROUP_ID));
        }

        @Test
        @DisplayName("removeMember 管理员移除普通成员")
        void adminRemoveMemberOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            ImConversationMember target = memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                    .thenReturn(memberRow(ADMIN_ID, ImConversationMember.ROLE_ADMIN))
                    .thenReturn(target);

            service.removeMember(ADMIN_ID, GROUP_ID, MEMBER_ID);
            verify(memberMapper).deleteById(target.getId());
        }

        @Test
        @DisplayName("ownerApprove 策略普通成员不能拉人")
        void invitePolicyOwnerApproveBlocksMember() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            g.setInvitePolicy("ownerApprove");
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(MEMBER_ID, ImConversationMember.ROLE_MEMBER));

            AddGroupMembersDTO dto = new AddGroupMembersDTO();
            dto.setMemberIds(List.of(99L));
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.addMembers(MEMBER_ID, GROUP_ID, dto));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("未知邀请策略拒绝")
        void unknownInvitePolicyRejected() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            g.setInvitePolicy("customPolicy");
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));

            AddGroupMembersDTO dto = new AddGroupMembersDTO();
            dto.setMemberIds(List.of(99L));
            assertThrows(CustomException.class, () -> service.addMembers(OWNER_ID, GROUP_ID, dto));
        }

        @Test
        @DisplayName("updateMuteAll 开启全体禁言")
        void updateMuteAllOk() {
            ImConversation g = group(GROUP_ID, OWNER_ID);
            when(conversationMapper.selectOneById(GROUP_ID)).thenReturn(g);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER))
                    .thenReturn(memberRow(OWNER_ID, ImConversationMember.ROLE_OWNER));
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            when(sysUserMapper.selectOneById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "U" + inv.getArgument(0)));

            MuteAllDTO dto = new MuteAllDTO();
            dto.setEnabled(true);
            dto.setEndTime(System.currentTimeMillis() + 3_600_000L);

            withConversationUpdateChain(() -> {
                GroupConversationVO vo = service.updateMuteAll(OWNER_ID, GROUP_ID, dto);
                assertEquals(Boolean.TRUE, vo.getMuteAll());
            });
            assertEquals(1, g.getMuteAll());
            verify(imPushService).pushActionToConversationMembers(eq(GROUP_ID), eq("group_mute_all_changed"), anyMap());
        }
    }
}
