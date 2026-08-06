package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserSearchVO;
import com.linkx.server.entity.SysFriendRequest;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysFriendRequestMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.UserPreferenceService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FriendServiceImpl 好友")
class FriendServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long PEER_ID = 20L;

    @Mock SysUserMapper sysUserMapper;
    @Mock SysUserRelationMapper sysUserRelationMapper;
    @Mock SysFriendRequestMapper sysFriendRequestMapper;
    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock ImMessageMapper messageMapper;
    @Mock MediaUrlService mediaUrlService;
    @Mock UserPreferenceService userPreferenceService;
    @Mock PresenceService presenceService;
    @Mock ImMessagePushService imPushService;
    @Mock ChatService chatService;

    private FriendServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> {
            Object a = inv.getArgument(0);
            return a == null ? null : "https://cdn/" + a;
        });
        when(userPreferenceService.showsOnlineStatus(anyLong())).thenReturn(true);
        when(presenceService.isOnline(anyLong())).thenReturn(false);
        service = new FriendServiceImpl(
                sysUserMapper, sysUserRelationMapper, sysFriendRequestMapper,
                conversationMapper, memberMapper, messageMapper, mediaUrlService,
                userPreferenceService, presenceService, imPushService, chatService
        );
    }

    private SysUser user(long id, String username) {
        return SysUser.builder().id(id).username(username).nickname("N" + id).avatar("a" + id).status(1).build();
    }

    @Nested
    @DisplayName("searchUsers")
    class Search {
        @Test
        @DisplayName("关键词过短")
        void tooShort() {
            assertThrows(CustomException.class, () -> service.searchUsers("a", USER_ID));
        }

        @Test
        @DisplayName("精确匹配 username")
        void exact() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(PEER_ID, "bob"));
            List<UserSearchVO> list = service.searchUsers("bob", USER_ID);
            assertEquals(1, list.size());
            assertEquals("bob", list.get(0).getUsername());
        }

        @Test
        @DisplayName("模糊合并 username/nickname")
        void fuzzy() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(user(PEER_ID, "bobby")))
                    .thenReturn(List.of(user(30L, "alice"), user(PEER_ID, "bobby")));
            List<UserSearchVO> list = service.searchUsers("bo", USER_ID);
            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("可搜到自己")
        void includeSelf() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(USER_ID, "me"));
            List<UserSearchVO> list = service.searchUsers("me", USER_ID);
            assertEquals(1, list.size());
            assertEquals(USER_ID, list.get(0).getId());
        }
    }

    @Nested
    @DisplayName("好友请求")
    class Requests {
        @Test
        @DisplayName("发送请求：目标不存在")
        void targetMissing() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            SendFriendRequestDTO dto = new SendFriendRequestDTO();
            dto.setUsername("ghost");
            assertThrows(CustomException.class, () -> service.sendFriendRequest(USER_ID, dto));
        }

        @Test
        @DisplayName("发送请求：不能加自己")
        void self() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(USER_ID, "me"));
            SendFriendRequestDTO dto = new SendFriendRequestDTO();
            dto.setUsername("me");
            assertThrows(CustomException.class, () -> service.sendFriendRequest(USER_ID, dto));
        }

        @Test
        @DisplayName("发送请求成功")
        void sendOk() {
            when(sysUserMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user(PEER_ID, "bob"));
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysFriendRequestMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(userPreferenceService.requiresFriendVerify(PEER_ID)).thenReturn(true);

            SendFriendRequestDTO dto = new SendFriendRequestDTO();
            dto.setUsername("bob");
            dto.setMessage("hi");
            service.sendFriendRequest(USER_ID, dto);
            verify(sysFriendRequestMapper).insert(any(SysFriendRequest.class));
            verify(imPushService).pushToUser(eq(PEER_ID), eq("notification_refresh"), anyMap());
        }

        @Test
        @DisplayName("listIncoming / listOutgoing")
        void listRequests() {
            SysFriendRequest req = SysFriendRequest.builder()
                    .id(1L).fromUserId(PEER_ID).toUserId(USER_ID)
                    .status(SysFriendRequest.STATUS_PENDING).createTime(new Date()).build();
            when(sysFriendRequestMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(req));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "bob")));

            List<FriendRequestVO> incoming = service.listIncomingRequests(USER_ID);
            assertEquals(1, incoming.size());
            assertEquals(1, service.listOutgoingRequests(USER_ID).size());
        }

        @Test
        @DisplayName("rejectFriendRequest")
        void reject() {
            SysFriendRequest req = SysFriendRequest.builder()
                    .id(1L).fromUserId(PEER_ID).toUserId(USER_ID)
                    .status(SysFriendRequest.STATUS_PENDING).build();
            when(sysFriendRequestMapper.selectOneById(1L)).thenReturn(req);
            service.rejectFriendRequest(USER_ID, 1L);
            assertEquals(SysFriendRequest.STATUS_REJECTED, req.getStatus());
            verify(sysFriendRequestMapper).update(req);
        }

        @Test
        @DisplayName("clearProcessedFriendRequests 仅删除已处理记录")
        void clearProcessed() {
            when(sysFriendRequestMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(2, 1);
            assertEquals(3, service.clearProcessedFriendRequests(USER_ID));
            verify(sysFriendRequestMapper, times(2)).deleteByQuery(any(QueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("关系维护")
    class Relations {
        @Test
        @DisplayName("listFriends")
        void listFriends() {
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUserRelation.builder().userId(USER_ID).friendId(PEER_ID).status(1).remark("R").build()
            ));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "bob")));
            List<FriendItemVO> list = service.listFriends(USER_ID);
            assertEquals(1, list.size());
            assertEquals("R", list.get(0).getRemark());
        }

        @Test
        @DisplayName("updateFriendRemark / group / block / unblock / isBlocked")
        void mutate() {
            SysUserRelation rel = SysUserRelation.builder()
                    .id(1L).userId(USER_ID).friendId(PEER_ID).status(1).build();
            when(sysUserRelationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(rel);
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

            assertEquals("Buddy", service.updateFriendRemark(USER_ID, PEER_ID, "Buddy"));
            assertEquals("Work", service.updateFriendGroup(USER_ID, PEER_ID, "Work"));
            service.blockFriend(USER_ID, PEER_ID);
            assertEquals(2, rel.getStatus());
            service.unblockFriend(USER_ID, PEER_ID);
            assertEquals(1, rel.getStatus());
            assertTrue(service.isBlocked(USER_ID, PEER_ID));
        }

        @Test
        @DisplayName("deleteFriend 成功")
        void deleteFriend() {
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(sysUserRelationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    SysUserRelation.builder().id(1L).userId(USER_ID).friendId(PEER_ID).status(1).build()
            );
            when(conversationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            service.deleteFriend(USER_ID, PEER_ID);
            verify(imPushService, times(2)).pushToUser(anyLong(), eq("notification_refresh"), anyMap());
        }
    }
}
