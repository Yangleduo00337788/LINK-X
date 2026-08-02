package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.metrics.LinkxMetrics;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ChatSearchHitVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRelation;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.RedPacketMapper;
import com.linkx.server.mapper.RedPacketRecordMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRelationMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.UserPreferenceService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.AdminRiskEventService;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatServiceImpl 单元测试")
class ChatServiceImplUnitTest {

    private static final long USER_ID = 10L;
    private static final long PEER_ID = 20L;
    private static final long CONV_ID = 100L;

    @Mock ImConversationMapper conversationMapper;
    @Mock ImConversationMemberMapper memberMapper;
    @Mock ImMessageMapper messageMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysUserRelationMapper sysUserRelationMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RedPacketMapper redPacketMapper;
    @Mock RedPacketRecordMapper redPacketRecordMapper;
    @Mock UserPreferenceService userPreferenceService;
    @Mock PresenceService presenceService;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock MessageStormService messageStormService;
    @Mock AuditLogService auditLogService;
    @Mock AdminRiskEventService adminRiskEventService;
    @Mock ObjectProvider<AdminReviewService> adminReviewService;
    @Mock LinkxMetrics linkxMetrics;

    private ChatServiceImpl service;
    private LinkxProperties linkxProperties;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getMinio().setMaxFileSize(10 * 1024 * 1024L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(adminReviewService.getIfAvailable()).thenReturn(null);
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        when(mediaUrlService.resolveFile(any())).thenAnswer(inv -> "https://cdn/file/" + inv.getArgument(0));
        service = new ChatServiceImpl(
                conversationMapper, memberMapper, messageMapper, sysUserMapper, sysUserRelationMapper,
                fileStorageService, mediaUrlService, objectKeyOwnershipService, linkxProperties, redisTemplate,
                redPacketMapper, redPacketRecordMapper, userPreferenceService, presenceService,
                sensitiveWordService, messageStormService, auditLogService, adminRiskEventService,
                adminReviewService, linkxMetrics
        );
    }

    private ImConversationMember membership(long convId, long userId, int pinned, int important, int muted) {
        return ImConversationMember.builder()
                .conversationId(convId)
                .userId(userId)
                .pinned(pinned)
                .important(important)
                .muted(muted)
                .lastReadMessageId(50L)
                .build();
    }

    private ImConversation privateConv(long id, Date lastMsgTime) {
        return ImConversation.builder()
                .id(id)
                .type(ImConversation.TYPE_PRIVATE)
                .privateKey("10_20")
                .lastMessageContent("hi")
                .lastMessageTime(lastMsgTime)
                .build();
    }

    private ImConversation groupConv(long id, String name, Date lastMsgTime) {
        return ImConversation.builder()
                .id(id)
                .type(ImConversation.TYPE_GROUP)
                .name(name)
                .ownerId(USER_ID)
                .lastMessageContent("[系统]")
                .lastMessageTime(lastMsgTime)
                .build();
    }

    private SysUser user(long id, String nickname) {
        return SysUser.builder().id(id).username("u" + id).nickname(nickname).avatar("av" + id).status(1).build();
    }

    private void stubMember(long userId, long convId) {
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                ImConversationMember.builder().conversationId(convId).userId(userId).lastReadMessageId(50L).build()
        );
    }

    private void stubPrivateChatAllowed() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                ImConversationMember.builder().userId(USER_ID).build(),
                ImConversationMember.builder().userId(PEER_ID).build()
        ));
        // blocked self→peer, peer→self, then friend
        when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 0L, 1L);
    }

    private void stubInsertMessage(long id) {
        when(messageMapper.insert(any(ImMessage.class))).thenAnswer(inv -> {
            ImMessage m = inv.getArgument(0);
            m.setId(id);
            if (m.getCreateTime() == null) {
                m.setCreateTime(new Date());
            }
            return 1;
        });
    }

    @Nested
    @DisplayName("listConversations")
    class ListConversations {
        @Test
        @DisplayName("无成员关系返回空列表")
        void emptyMemberships() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            assertTrue(service.listConversations(USER_ID).isEmpty());
        }

        @Test
        @DisplayName("私聊与群聊排序及未读数")
        void privateAndGroupSorted() {
            Date older = new Date(System.currentTimeMillis() - 60_000);
            Date newer = new Date();
            ImConversationMember m1 = membership(101L, USER_ID, 0, 0, 0);
            ImConversationMember m2 = membership(102L, USER_ID, 1, 0, 0);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(m1, m2))
                    .thenReturn(List.of(
                            ImConversationMember.builder().conversationId(101L).userId(USER_ID).role(ImConversationMember.ROLE_MEMBER).build(),
                            ImConversationMember.builder().conversationId(101L).userId(PEER_ID).role(ImConversationMember.ROLE_MEMBER).build()
                    ))
                    .thenReturn(List.of(
                            ImConversationMember.builder().conversationId(102L).userId(USER_ID).role(ImConversationMember.ROLE_OWNER).build(),
                            ImConversationMember.builder().conversationId(102L).userId(30L).role(ImConversationMember.ROLE_MEMBER).createTime(older).build()
                    ));

            ImConversation priv = privateConv(101L, newer);
            ImConversation grp = groupConv(102L, "Team", older);
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(new ArrayList<>(List.of(priv, grp)));

            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenAnswer(inv ->
                    ImConversationMember.builder().userId(USER_ID).lastReadMessageId(50L).build()
            );

            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(user(PEER_ID, "Bob")))
                    .thenReturn(List.of(user(PEER_ID, "Bob"), user(30L, "Carol"), user(USER_ID, "Alice")));
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUserRelation.builder().friendId(PEER_ID).status(1).remark("Buddy").build()
            ));
            when(userPreferenceService.batchShowsOnlineStatus(anySet())).thenReturn(Map.of(PEER_ID, true));
            when(presenceService.isOnline(PEER_ID)).thenReturn(true);
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().userId(USER_ID).lastReadMessageId(50L).build()
            );

            List<ConversationVO> list = service.listConversations(USER_ID);
            assertEquals(2, list.size());
            assertEquals(102L, list.get(0).getId());
            assertTrue(list.get(0).getPinned());
            ConversationVO privVo = list.stream().filter(c -> c.getId() == 101L).findFirst().orElseThrow();
            assertEquals("Buddy", privVo.getPeerRemark());
            assertEquals(Boolean.TRUE, privVo.getPeerOnline());
            assertEquals(3L, privVo.getUnreadCount());
        }

        @Test
        @DisplayName("私聊拉黑仍展示且标记 blocked")
        void privateBlockedStillListed() {
            ImConversationMember m = membership(101L, USER_ID, 0, 0, 0);
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(m))
                    .thenReturn(List.of(
                            ImConversationMember.builder().conversationId(101L).userId(USER_ID).build(),
                            ImConversationMember.builder().conversationId(101L).userId(PEER_ID).build()
                    ));
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>(List.of(privateConv(101L, new Date()))));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(user(PEER_ID, "Bob")));
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUserRelation.builder().friendId(PEER_ID).status(2).remark("Blocked").build()
            ));
            when(userPreferenceService.batchShowsOnlineStatus(anySet())).thenReturn(Map.of(PEER_ID, true));
            when(presenceService.isOnline(PEER_ID)).thenReturn(false);
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(1L).build()
            );

            List<ConversationVO> list = service.listConversations(USER_ID);
            assertEquals(1, list.size());
            assertEquals(Boolean.TRUE, list.get(0).getBlocked());
        }

        @Test
        @DisplayName("异常好友状态私聊会话跳过")
        void privateAbnormalRelationSkipped() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(membership(101L, USER_ID, 0, 0, 0)))
                    .thenReturn(List.of(
                            ImConversationMember.builder().conversationId(101L).userId(USER_ID).build(),
                            ImConversationMember.builder().conversationId(101L).userId(PEER_ID).build()
                    ));
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>(List.of(privateConv(101L, new Date()))));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(user(PEER_ID, "Bob")));
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUserRelation.builder().friendId(PEER_ID).status(0).build()
            ));

            assertTrue(service.listConversations(USER_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("getOrCreatePrivateConversation")
    class OpenPrivate {
        @Test
        @DisplayName("不能与自己聊天")
        void selfChat() {
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.getOrCreatePrivateConversation(USER_ID, USER_ID));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("非好友且未开陌生人会话")
        void strangerBlocked() {
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(userPreferenceService.allowsStrangerChat(PEER_ID)).thenReturn(false);
            when(userPreferenceService.allowsStrangerChat(USER_ID)).thenReturn(false);

            CustomException ex = assertThrows(CustomException.class,
                    () -> service.getOrCreatePrivateConversation(USER_ID, PEER_ID));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("好友不存在或已禁用")
        void friendMissing() {
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(sysUserMapper.selectOneById(PEER_ID)).thenReturn(null);

            assertThrows(CustomException.class, () -> service.getOrCreatePrivateConversation(USER_ID, PEER_ID));

            when(sysUserMapper.selectOneById(PEER_ID)).thenReturn(
                    SysUser.builder().id(PEER_ID).username("u20").nickname("Bob").status(0).build()
            );
            assertThrows(CustomException.class, () -> service.getOrCreatePrivateConversation(USER_ID, PEER_ID));
        }

        @Test
        @DisplayName("陌生人会话允许")
        void strangerAllowed() {
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(userPreferenceService.allowsStrangerChat(PEER_ID)).thenReturn(true);
            when(sysUserMapper.selectOneById(PEER_ID)).thenReturn(user(PEER_ID, "Bob"));
            when(conversationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(conversationMapper.insert(any(ImConversation.class))).thenAnswer(inv -> {
                ImConversation c = inv.getArgument(0);
                c.setId(CONV_ID);
                return 1;
            });
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().conversationId(CONV_ID).userId(USER_ID).build()
            );
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            ConversationVO vo = service.getOrCreatePrivateConversation(USER_ID, PEER_ID);
            assertNotNull(vo);
            verify(conversationMapper).insert(any(ImConversation.class));
        }

        @Test
        @DisplayName("已有私聊会话直接返回")
        void existingConversation() {
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
            when(sysUserMapper.selectOneById(PEER_ID)).thenReturn(user(PEER_ID, "Bob"));
            when(conversationMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(privateConv(CONV_ID, new Date()));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().conversationId(CONV_ID).userId(USER_ID).build()
            );
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

            ConversationVO vo = service.getOrCreatePrivateConversation(USER_ID, PEER_ID);
            assertEquals(CONV_ID, vo.getId());
            verify(conversationMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("searchMessages")
    class Search {
        @Test
        @DisplayName("空关键词返回空")
        void blankKeyword() {
            assertTrue(service.searchMessages(USER_ID, "  ", null, null, null, null, 10).isEmpty());
        }

        @Test
        @DisplayName("指定会话无权限")
        void forbiddenConversation() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(membership(99L, USER_ID, 0, 0, 0))
            );
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.searchMessages(USER_ID, "hello", null, CONV_ID, null, null, 10));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("命中内容与文件名")
        void hitsFromContentAndFileName() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(membership(CONV_ID, USER_ID, 0, 0, 0))
            );
            ImMessage byContent = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("hello world").createTime(new Date()).build();
            when(messageMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(byContent))
                    .thenReturn(List.of());

            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(groupConv(CONV_ID, "G", new Date()))
            );
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "Bob")));

            List<ChatSearchHitVO> hits = service.searchMessages(USER_ID, "hello", ImMessage.TYPE_TEXT, CONV_ID, null, null, 5);
            assertEquals(1, hits.size());
            assertTrue(hits.get(0).getHighlight().contains("<mark>"));
        }

        @Test
        @DisplayName("按文件名回退搜索并应用时间/type 过滤")
        void searchByFileNameWithFilters() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(membership(CONV_ID, USER_ID, 0, 0, 0))
            );
            ImMessage byFile = ImMessage.builder()
                    .id(2L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_FILE).fileName("report.pdf").content("")
                    .createTime(new Date()).build();
            when(messageMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of())
                    .thenReturn(List.of(byFile));
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(groupConv(CONV_ID, "G", new Date()))
            );
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "Bob")));

            long from = System.currentTimeMillis() - 86_400_000L;
            long to = System.currentTimeMillis();
            List<ChatSearchHitVO> hits = service.searchMessages(
                    USER_ID, "report", ImMessage.TYPE_FILE, CONV_ID, from, to, 5);
            assertEquals(1, hits.size());
            assertEquals(ImMessage.TYPE_FILE, hits.get(0).getType());
            assertTrue(hits.get(0).getHighlight().contains("<mark>"));
        }
    }

    @Test
    @DisplayName("buildSearchHighlight 转义并高亮")
    void buildSearchHighlight() {
        assertNull(ChatServiceImpl.buildSearchHighlight(null, "x"));
        assertEquals("plain", ChatServiceImpl.buildSearchHighlight("plain", ""));
        String hi = ChatServiceImpl.buildSearchHighlight("<b>Hello</b>", "hello");
        assertTrue(hi.contains("<mark>"));
        assertFalse(hi.contains("<b>"));
    }

    @Nested
    @DisplayName("assertConversationMember / 已读")
    class ReadGuards {
        @Test
        @DisplayName("非成员访问会话")
        void notMember() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.assertConversationMember(USER_ID, CONV_ID));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("markAsRead 更新已读位并返回未读")
        void markAsReadUpdates() {
            ImConversationMember member = ImConversationMember.builder()
                    .conversationId(CONV_ID).userId(USER_ID).lastReadMessageId(10L).build();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);

            long unread = service.markAsRead(USER_ID, CONV_ID, 20L);
            assertEquals(2L, unread);
            assertEquals(20L, member.getLastReadMessageId());
            verify(memberMapper).update(member);
        }

        @Test
        @DisplayName("markAsRead 取 max 不回退已读位")
        void markAsReadKeepsHigherWatermark() {
            ImConversationMember member = ImConversationMember.builder()
                    .conversationId(CONV_ID).userId(USER_ID).lastReadMessageId(50L).build();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

            service.markAsRead(USER_ID, CONV_ID, 30L);
            assertEquals(50L, member.getLastReadMessageId());
            verify(memberMapper, never()).update(any());
        }

        @Test
        @DisplayName("markAsRead null 消息 ID 不更新")
        void markAsReadNullId() {
            ImConversationMember member = ImConversationMember.builder()
                    .conversationId(CONV_ID).userId(USER_ID).lastReadMessageId(10L).build();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

            service.markAsRead(USER_ID, CONV_ID, null);
            assertEquals(10L, member.getLastReadMessageId());
            verify(memberMapper, never()).update(any());
        }

        @Test
        @DisplayName("总未读汇总")
        void totalUnread() {
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(
                            ImConversationMember.builder().conversationId(1L).userId(USER_ID).lastReadMessageId(1L).build(),
                            ImConversationMember.builder().conversationId(2L).userId(USER_ID).lastReadMessageId(null).build()
                    )
            );
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(ImConversationMember.builder().lastReadMessageId(1L).build())
                    .thenReturn(ImConversationMember.builder().lastReadMessageId(null).build());
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L, 5L);

            assertEquals(7L, service.getTotalUnreadCount(USER_ID));
        }
    }

    @Nested
    @DisplayName("listMessages / listPrivatePeerIds")
    class MessagesAndPeers {
        @Test
        @DisplayName("listMessages 空结果")
        void emptyMessages() {
            stubMember(USER_ID, CONV_ID);
            when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
            assertTrue(service.listMessages(USER_ID, CONV_ID, null, 0).isEmpty());
        }

        @Test
        @DisplayName("listMessages 默认分页上限")
        void listMessagesWithSenders() {
            stubMember(USER_ID, CONV_ID);
            ImMessage msg = ImMessage.builder()
                    .id(100L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("hi").createTime(new Date()).build();
            when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(new ArrayList<>(List.of(msg)));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(99L).build()
            );
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "Bob")));

            List<MessageVO> list = service.listMessages(USER_ID, CONV_ID, null, 200);
            assertEquals(1, list.size());
            assertEquals("hi", list.get(0).getContent());
        }

        @Test
        @DisplayName("listMessages beforeMessageId 游标分页")
        void listMessagesBeforeCursor() {
            stubMember(USER_ID, CONV_ID);
            ImMessage older = ImMessage.builder()
                    .id(150L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("older").createTime(new Date()).build();
            when(messageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(new ArrayList<>(List.of(older)));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(140L).build()
            );
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(PEER_ID, "Bob")));

            List<MessageVO> list = service.listMessages(USER_ID, CONV_ID, 200L, 50);
            assertEquals(1, list.size());
            assertEquals(150L, list.get(0).getId());
        }

        @Test
        @DisplayName("getUnreadCount 基于 lastReadMessageId")
        void getUnreadCountWithLastRead() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder()
                            .conversationId(CONV_ID).userId(USER_ID).lastReadMessageId(100L).build()
            );
            when(messageMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(4L);

            assertEquals(4L, service.getUnreadCount(USER_ID, CONV_ID));
        }

        @Test
        @DisplayName("listPrivatePeerIds")
        void privatePeerIds() {
            assertTrue(service.listPrivatePeerIds(null).isEmpty());
            when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(membership(101L, USER_ID, 0, 0, 0)))
                    .thenReturn(List.of(
                            ImConversationMember.builder().conversationId(101L).userId(PEER_ID).build()
                    ));
            when(conversationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(
                    List.of(privateConv(101L, new Date()))
            );
            assertEquals(List.of(PEER_ID), service.listPrivatePeerIds(USER_ID));
        }
    }

    @Nested
    @DisplayName("附件与媒体")
    class FileGuards {
        @Test
        @DisplayName("openMessageFile 各类拒绝")
        void openMessageFileGuards() {
            when(messageMapper.selectOneById(1L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.openMessageFile(USER_ID, 1L));

            ImMessage noFile = ImMessage.builder().id(1L).conversationId(CONV_ID).fileUrl("").build();
            when(messageMapper.selectOneById(1L)).thenReturn(noFile);
            stubMember(USER_ID, CONV_ID);
            assertThrows(CustomException.class, () -> service.openMessageFile(USER_ID, 1L));

            ImMessage redPacket = ImMessage.builder()
                    .id(2L).conversationId(CONV_ID).type(ImMessage.TYPE_RED_PACKET).fileUrl("1").build();
            when(messageMapper.selectOneById(2L)).thenReturn(redPacket);
            assertThrows(CustomException.class, () -> service.openMessageFile(USER_ID, 2L));
        }

        @Test
        @DisplayName("openMessageFile 成功打开附件")
        void openMessageFileSuccess() {
            ImMessage msg = ImMessage.builder()
                    .id(3L).conversationId(CONV_ID).type(ImMessage.TYPE_FILE)
                    .fileUrl("files/doc.pdf").fileName("doc.pdf").build();
            when(messageMapper.selectOneById(3L)).thenReturn(msg);
            stubMember(USER_ID, CONV_ID);
            FileStorageService.StoredObject stored = new FileStorageService.StoredObject(
                    new java.io.ByteArrayInputStream(new byte[]{1}), "application/pdf", 1L, "files/doc.pdf");
            when(fileStorageService.openObject("files/doc.pdf")).thenReturn(stored);

            assertSame(stored, service.openMessageFile(USER_ID, 3L));
        }

        @Test
        @DisplayName("getMessageFileName 回退默认名")
        void messageFileName() {
            ImMessage msg = ImMessage.builder().id(1L).conversationId(CONV_ID).fileName("  ").build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            stubMember(USER_ID, CONV_ID);
            assertEquals("file", service.getMessageFileName(USER_ID, 1L));
        }

        @Test
        @DisplayName("refreshMessageMediaUrl 从 content 回退")
        void refreshMediaFromContent() {
            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).type(ImMessage.TYPE_IMAGE)
                    .fileUrl("").content("img/key.png").build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            stubMember(USER_ID, CONV_ID);
            when(mediaUrlService.resolveFile("img/key.png")).thenReturn("https://signed");

            assertEquals("https://signed", service.refreshMessageMediaUrl(USER_ID, 1L));
        }
    }

    @Nested
    @DisplayName("系统/会议消息")
    class SystemMessages {
        @Test
        @DisplayName("postSystemMessage 校验")
        void postSystemValidation() {
            assertThrows(CustomException.class, () -> service.postSystemMessage(USER_ID, CONV_ID, "  "));
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.postSystemMessage(USER_ID, CONV_ID, "tip"));
        }

        @Test
        @DisplayName("postSystemMessage 成功")
        void postSystemOk() {
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(groupConv(CONV_ID, "G", null));
            when(messageMapper.insert(any(ImMessage.class))).thenAnswer(inv -> {
                ImMessage m = inv.getArgument(0);
                m.setId(500L);
                m.setCreateTime(new Date());
                return 1;
            });
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(null).build()
            );
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            MessageVO vo = service.postSystemMessage(USER_ID, CONV_ID, "  hello  ");
            assertEquals(ImMessage.TYPE_SYSTEM, vo.getType());
            verify(conversationMapper).update(any(ImConversation.class));
        }

        @Test
        @DisplayName("会议/通话邀请参数校验")
        void conferenceAndCallValidation() {
            assertThrows(CustomException.class,
                    () -> service.postConferenceInviteMessage(null, CONV_ID, 1L, "t", "video", "call", false));
            assertThrows(CustomException.class,
                    () -> service.postCallInviteMessage(USER_ID, CONV_ID, " ", "voice"));

            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(null);
            assertThrows(CustomException.class,
                    () -> service.postCallInviteMessage(USER_ID, CONV_ID, "call-1", "voice"));
        }

        @Test
        @DisplayName("updateCallTipMessage 缺参或找不到")
        void updateCallTip() {
            assertNull(service.updateCallTipMessage(null, "c1", "x"));
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            assertNull(service.updateCallTipMessage(CONV_ID, "c1", "done"));
        }
    }

    @Nested
    @DisplayName("会话开关与撤回")
    class TogglesAndRecall {
        @Test
        @DisplayName("togglePin 切换")
        void togglePin() {
            ImConversationMember member = ImConversationMember.builder()
                    .conversationId(CONV_ID).userId(USER_ID).pinned(0).build();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            service.togglePinConversation(USER_ID, CONV_ID);
            assertEquals(1, member.getPinned());
            verify(memberMapper).update(member);
        }

        @Test
        @DisplayName("recallMessage 权限与时限")
        void recallGuards() {
            stubMember(USER_ID, CONV_ID);
            when(messageMapper.selectOneById(1L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.recallMessage(USER_ID, CONV_ID, 1L));

            ImMessage others = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(PEER_ID).type(ImMessage.TYPE_TEXT)
                    .createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(others);
            assertThrows(CustomException.class, () -> service.recallMessage(USER_ID, CONV_ID, 1L));

            ImMessage expired = ImMessage.builder()
                    .id(2L).conversationId(CONV_ID).senderId(USER_ID).type(ImMessage.TYPE_TEXT)
                    .createTime(new Date(System.currentTimeMillis() - 300_000)).build();
            when(messageMapper.selectOneById(2L)).thenReturn(expired);
            assertThrows(CustomException.class, () -> service.recallMessage(USER_ID, CONV_ID, 2L));
        }
    }

    @Nested
    @DisplayName("分片上传与秒传")
    class MultipartAndHash {
        @Test
        @DisplayName("initiateMultipartUpload 超大文件")
        void multipartTooLarge() {
            stubMember(USER_ID, CONV_ID);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.initiateMultipartUpload(USER_ID, CONV_ID, "a.jpg", "image/jpeg", 20_000_000L));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("initiateMultipartUpload 非法扩展名")
        void multipartBadExt() {
            stubMember(USER_ID, CONV_ID);
            when(fileStorageService.allocateObjectName("bad.exe")).thenThrow(new IllegalArgumentException("bad ext"));
            assertThrows(CustomException.class,
                    () -> service.initiateMultipartUpload(USER_ID, CONV_ID, "bad.exe", "application/octet-stream", null));
        }

        @Test
        @DisplayName("findFileByHash 非属主返回 null")
        void findFileByHashNotOwned() {
            when(fileStorageService.getObjectKeyByHashInternal(anyString())).thenReturn("k1");
            when(objectKeyOwnershipService.isOwned(USER_ID, "k1")).thenReturn(false);
            assertNull(service.findFileByHash(USER_ID, "a".repeat(64)));
        }
    }

    @Nested
    @DisplayName("sendMessage 校验")
    class SendValidation {
        @Test
        @DisplayName("非成员不能发消息")
        void notMember() {
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("hi");
            assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
        }

        @Test
        @DisplayName("文本消息缺内容")
        void textEmpty() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("  ");
            assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
        }

        @Test
        @DisplayName("私聊文本发送成功")
        void privateTextOk() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("hello", false, false, false, List.of())
            );
            stubInsertMessage(700L);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("hello");
            MessageVO vo = service.sendMessage(USER_ID, dto);
            assertEquals("hello", vo.getContent());
            assertEquals(ImMessage.TYPE_TEXT, vo.getType());
            verify(messageMapper).insert(any(ImMessage.class));
            verify(conversationMapper).update(any(ImConversation.class));
            verify(linkxMetrics).recordMessageSent();
        }

        @Test
        @DisplayName("群聊敏感词告警仍可发送")
        void groupSensitiveAlert() {
            stubMember(USER_ID, CONV_ID);
            ImConversation grp = groupConv(CONV_ID, "G", new Date());
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(grp);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder()
                            .conversationId(CONV_ID).userId(USER_ID)
                            .role(ImConversationMember.ROLE_OWNER)
                            .lastReadMessageId(1L).build()
            );
            when(valueOps.get(startsWith("linkx:storm:"))).thenReturn("10");
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("***", true, false, true, List.of("bad"))
            );
            stubInsertMessage(701L);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("bad word");
            MessageVO vo = service.sendMessage(USER_ID, dto);
            assertEquals("***", vo.getContent());
            assertEquals(Boolean.TRUE, vo.getSensitiveAlert());
            verify(auditLogService).log(any(), anyString(), eq(USER_ID), any(), any(), any(), eq(true), eq("filtered"));
            verify(adminRiskEventService).recordSensitiveMatch(eq(USER_ID), eq("bad"), eq("filtered"), eq(CONV_ID));
        }

        @Test
        @DisplayName("clientMsgId 去重返回已有消息")
        void clientMsgDedupHit() {
            stubMember(USER_ID, CONV_ID);
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(false);
            ImMessage existing = ImMessage.builder()
                    .id(55L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("dup").clientMsgId("c1")
                    .createTime(new Date()).deleted(0).build();
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("dup");
            dto.setClientMsgId("c1");
            MessageVO vo = service.sendMessage(USER_ID, dto);
            assertEquals(55L, vo.getId());
            verify(messageMapper, never()).insert(any(ImMessage.class));
        }

        @Test
        @DisplayName("clientMsgId 占用中返回 409")
        void clientMsgDedupBusy() {
            stubMember(USER_ID, CONV_ID);
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(false);
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("hi");
            dto.setClientMsgId("c2");
            CustomException ex = assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("敏感词拦截")
        void sensitiveBlocked() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("x", false, true, false, List.of("banned"))
            );

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("banned");
            CustomException ex = assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
            assertEquals(400, ex.getCode());
            verify(messageMapper, never()).insert(any(ImMessage.class));
        }

        @Test
        @DisplayName("图片消息发送成功")
        void sendImageMessage() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(fileStorageService.extractObjectKey("img/key.png")).thenReturn("img/key.png");
            doNothing().when(objectKeyOwnershipService).assertOwned(USER_ID, "img/key.png");
            stubInsertMessage(710L);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("image");
            dto.setFileUrl("img/key.png");
            MessageVO vo = service.sendMessage(USER_ID, dto);
            assertEquals(ImMessage.TYPE_IMAGE, vo.getType());
            assertEquals("https://cdn/file/img/key.png", vo.getFileUrl());
        }

        @Test
        @DisplayName("文件消息发送成功")
        void sendFileMessage() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(fileStorageService.extractObjectKey("files/a.txt")).thenReturn("files/a.txt");
            doNothing().when(objectKeyOwnershipService).assertOwned(USER_ID, "files/a.txt");
            stubInsertMessage(711L);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("file");
            dto.setFileUrl("files/a.txt");
            dto.setFileName("a.txt");
            dto.setFileSize(128L);
            MessageVO vo = service.sendMessage(USER_ID, dto);
            assertEquals(ImMessage.TYPE_FILE, vo.getType());
            assertEquals("a.txt", vo.getFileName());
        }

        @Test
        @DisplayName("语音/位置消息发送与校验")
        void voiceAndLocationMessages() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    ImConversationMember.builder().userId(USER_ID).build(),
                    ImConversationMember.builder().userId(PEER_ID).build()
            ));
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 0L, 1L, 0L, 0L, 1L);
            when(fileStorageService.extractObjectKey("voice/a.opus")).thenReturn("voice/a.opus");
            doNothing().when(objectKeyOwnershipService).assertOwned(USER_ID, "voice/a.opus");
            stubInsertMessage(720L);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO voice = new SendMessageDTO();
            voice.setConversationId(CONV_ID);
            voice.setMsgType("voice");
            voice.setFileUrl("voice/a.opus");
            voice.setVoiceDuration(5);
            MessageVO voiceVo = service.sendMessage(USER_ID, voice);
            assertEquals(ImMessage.TYPE_VOICE, voiceVo.getType());
            assertEquals(5, voiceVo.getVoiceDuration());

            stubInsertMessage(721L);
            SendMessageDTO loc = new SendMessageDTO();
            loc.setConversationId(CONV_ID);
            loc.setMsgType("location");
            loc.setContent("北京市朝阳区");
            MessageVO locVo = service.sendMessage(USER_ID, loc);
            assertEquals(ImMessage.TYPE_LOCATION, locVo.getType());

            SendMessageDTO badVoice = new SendMessageDTO();
            badVoice.setConversationId(CONV_ID);
            badVoice.setMsgType("voice");
            badVoice.setFileUrl("voice/b.opus");
            assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, badVoice));
        }

        @Test
        @DisplayName("私聊已屏蔽不能发消息")
        void privateBlockedCannotSend() {
            stubMember(USER_ID, CONV_ID);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    ImConversationMember.builder().userId(USER_ID).build(),
                    ImConversationMember.builder().userId(PEER_ID).build()
            ));
            when(sysUserRelationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L, 0L);

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("hi");
            CustomException ex = assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("群聊消息风暴超限")
        void groupStormLimitExceeded() {
            stubMember(USER_ID, CONV_ID);
            ImConversation grp = groupConv(CONV_ID, "BigG", new Date());
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(grp);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder()
                            .conversationId(CONV_ID).userId(USER_ID)
                            .role(ImConversationMember.ROLE_OWNER).build()
            );
            when(valueOps.get("linkx:storm:" + CONV_ID + ":count")).thenReturn("600");
            doThrow(new CustomException(429, "群消息发送过于频繁"))
                    .when(messageStormService).checkAndRecordGroupStorm(USER_ID, CONV_ID, 600);

            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("storm");
            CustomException ex = assertThrows(CustomException.class, () -> service.sendMessage(USER_ID, dto));
            assertEquals(429, ex.getCode());
            verify(messageMapper, never()).insert(any(ImMessage.class));
        }
    }

    @Nested
    @DisplayName("adminForceRecall / edit / forward")
    class MoreMessageOps {
        @Test
        @DisplayName("adminForceRecall 消息不存在")
        void adminRecallMissing() {
            when(messageMapper.selectOneById(1L)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.adminForceRecallMessage(1L));
        }

        @Test
        @DisplayName("editMessage 非文本")
        void editNonText() {
            stubMember(USER_ID, CONV_ID);
            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_IMAGE).createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            assertThrows(CustomException.class,
                    () -> service.editMessage(USER_ID, CONV_ID, 1L, "new"));
        }

        @Test
        @DisplayName("forwardMessage 不能转发红包")
        void forwardRedPacket() {
            stubMember(USER_ID, CONV_ID);
            ImMessage src = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_RED_PACKET).createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(src);
            assertThrows(CustomException.class,
                    () -> service.forwardMessage(USER_ID, CONV_ID, 1L, CONV_ID));
        }

        @Test
        @DisplayName("toggleImportant / toggleMute")
        void toggleFlags() {
            ImConversationMember member = ImConversationMember.builder()
                    .conversationId(CONV_ID).userId(USER_ID).important(1).muted(1).build();
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(member);
            service.toggleImportantConversation(USER_ID, CONV_ID);
            service.toggleMuteConversation(USER_ID, CONV_ID);
            assertEquals(0, member.getImportant());
            assertEquals(0, member.getMuted());
        }

        @Test
        @DisplayName("getMemberCount 委托 mapper")
        void memberCount() {
            when(memberMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(7L);
            assertEquals(7L, service.getMemberCount(CONV_ID));
        }

        @Test
        @DisplayName("recallMessage 成功撤回")
        void recallSuccess() {
            stubMember(USER_ID, CONV_ID);
            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("hi").createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(groupConv(CONV_ID, "G", new Date()));
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

            MessageVO vo = service.recallMessage(USER_ID, CONV_ID, 1L);
            assertEquals(ImMessage.TYPE_RECALL, vo.getType());
            verify(messageMapper).update(msg);
        }

        @Test
        @DisplayName("editMessage 成功编辑")
        void editSuccess() {
            stubMember(USER_ID, CONV_ID);
            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("old").createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("new text", false, false, false, List.of())
            );
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(groupConv(CONV_ID, "G", new Date()));
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(msg);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            MessageVO vo = service.editMessage(USER_ID, CONV_ID, 1L, "  new text  ");
            assertEquals("new text", vo.getContent());
            verify(messageMapper).update(msg);
        }

        @Test
        @DisplayName("postConferenceInviteMessage 成功")
        void conferenceInviteOk() {
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(groupConv(CONV_ID, "G", null));
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            when(messageMapper.insert(any(ImMessage.class))).thenAnswer(inv -> {
                ImMessage m = inv.getArgument(0);
                m.setId(600L);
                m.setCreateTime(new Date());
                return 1;
            });
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(null).build()
            );

            MessageVO vo = service.postConferenceInviteMessage(
                    USER_ID, CONV_ID, 50L, "Weekly", "video", "meeting", true);
            assertEquals(ImMessage.TYPE_CONFERENCE, vo.getType());
        }

        @Test
        @DisplayName("updateCallTipMessage 更新成功")
        void updateCallTipOk() {
            ImMessage existing = ImMessage.builder()
                    .id(9L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_CONFERENCE).content("ringing").fileUrl("call-1").build();
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);
            when(sysUserMapper.selectOneById(any())).thenReturn(user(USER_ID, "Alice"));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(null).build()
            );

            MessageVO vo = service.updateCallTipMessage(CONV_ID, "call-1", "ended");
            assertNotNull(vo);
            verify(messageMapper).update(existing);
        }

        @Test
        @DisplayName("initiateMultipartUpload 成功")
        void multipartInitOk() {
            stubMember(USER_ID, CONV_ID);
            when(fileStorageService.allocateObjectName("photo.jpg")).thenReturn("2026/04/02/u.jpg");
            when(fileStorageService.initiateMultipartUpload(eq("2026/04/02/u.jpg"), eq("image/jpeg")))
                    .thenReturn(new FileStorageService.MultipartSession("up-1", "2026/04/02/u.jpg", "image/jpeg"));

            Map<String, Object> result = service.initiateMultipartUpload(
                    USER_ID, CONV_ID, "photo.jpg", "image/jpeg", 1024L);
            assertEquals("up-1", result.get("uploadId"));
            verify(valueOps).set(startsWith("linkx:mp:owner:"), eq(USER_ID + ":" + CONV_ID), any());
        }

        @Test
        @DisplayName("findFileByHash 属主命中")
        void findFileByHashOwned() {
            when(fileStorageService.getObjectKeyByHashInternal(anyString())).thenReturn("k1");
            when(objectKeyOwnershipService.isOwned(USER_ID, "k1")).thenReturn(true);
            assertEquals("k1", service.findFileByHash(USER_ID, "a".repeat(64)));
        }

        @Test
        @DisplayName("quoteMessage 引用消息不存在")
        void quoteMissing() {
            stubMember(USER_ID, CONV_ID);
            when(messageMapper.selectOneById(99L)).thenReturn(null);
            SendMessageDTO dto = new SendMessageDTO();
            dto.setConversationId(CONV_ID);
            dto.setMsgType("text");
            dto.setContent("reply");
            assertThrows(CustomException.class,
                    () -> service.quoteMessage(USER_ID, CONV_ID, 99L, dto));
        }

        @Test
        @DisplayName("adminForceRecall 成功")
        void adminRecallOk() {
            ImMessage msg = ImMessage.builder()
                    .id(1L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("x").createTime(new Date()).build();
            when(messageMapper.selectOneById(1L)).thenReturn(msg);
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(groupConv(CONV_ID, "G", new Date()));
            when(messageMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

            MessageVO vo = service.adminForceRecallMessage(1L);
            assertEquals(ImMessage.TYPE_RECALL, vo.getType());
            verify(messageMapper).update(msg);
        }

        @Test
        @DisplayName("postCallInviteMessage 成功")
        void callInviteOk() {
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, null));
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            stubInsertMessage(800L);
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder().lastReadMessageId(null).build()
            );

            MessageVO vo = service.postCallInviteMessage(USER_ID, CONV_ID, "call-xyz", "voice");
            assertEquals(ImMessage.TYPE_CONFERENCE, vo.getType());
            verify(conversationMapper).update(any(ImConversation.class));
        }

        @Test
        @DisplayName("forwardMessage 文本成功")
        void forwardTextOk() {
            stubMember(USER_ID, CONV_ID);
            ImMessage src = ImMessage.builder()
                    .id(11L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("fwd-me").createTime(new Date()).build();
            when(messageMapper.selectOneById(11L)).thenReturn(src);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("fwd-me", false, false, false, List.of())
            );
            stubInsertMessage(801L);
            when(messageMapper.selectOneById(801L)).thenAnswer(inv -> {
                ImMessage m = ImMessage.builder()
                        .id(801L).conversationId(CONV_ID).senderId(USER_ID)
                        .type(ImMessage.TYPE_TEXT).content("fwd-me").createTime(new Date()).build();
                return m;
            });
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);

            MessageVO vo = service.forwardMessage(USER_ID, CONV_ID, 11L, CONV_ID);
            assertEquals("fwd-me", vo.getContent());
            verify(messageMapper).update(any(ImMessage.class));
        }

        @Test
        @DisplayName("forwardMessage 跨会话转发到群聊")
        void forwardToGroupConversation() {
            long groupId = 200L;
            stubMember(USER_ID, CONV_ID);
            ImMessage src = ImMessage.builder()
                    .id(11L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("fwd-to-group").createTime(new Date()).build();
            when(messageMapper.selectOneById(11L)).thenReturn(src);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            when(conversationMapper.selectOneById(groupId)).thenReturn(groupConv(groupId, "Team", new Date()));
            when(memberMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                    ImConversationMember.builder()
                            .conversationId(groupId).userId(USER_ID)
                            .role(ImConversationMember.ROLE_OWNER)
                            .lastReadMessageId(1L).build()
            );
            when(valueOps.get(startsWith("linkx:storm:"))).thenReturn("10");
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("fwd-to-group", false, false, false, List.of())
            );
            stubInsertMessage(901L);
            when(messageMapper.selectOneById(901L)).thenAnswer(inv -> ImMessage.builder()
                    .id(901L).conversationId(groupId).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("fwd-to-group").createTime(new Date()).build());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));
            when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);

            MessageVO vo = service.forwardMessage(USER_ID, CONV_ID, 11L, groupId);
            assertEquals(groupId, vo.getConversationId());
            assertEquals("fwd-to-group", vo.getContent());
            verify(messageMapper).update(any(ImMessage.class));
        }

        @Test
        @DisplayName("quoteMessage 成功")
        void quoteOk() {
            stubMember(USER_ID, CONV_ID);
            ImMessage quoted = ImMessage.builder()
                    .id(12L).conversationId(CONV_ID).senderId(PEER_ID)
                    .type(ImMessage.TYPE_TEXT).content("orig").createTime(new Date()).build();
            when(messageMapper.selectOneById(12L)).thenReturn(quoted);
            when(conversationMapper.selectOneById(CONV_ID)).thenReturn(privateConv(CONV_ID, new Date()));
            stubPrivateChatAllowed();
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("reply", false, false, false, List.of())
            );
            stubInsertMessage(802L);
            when(messageMapper.selectOneById(802L)).thenAnswer(inv -> ImMessage.builder()
                    .id(802L).conversationId(CONV_ID).senderId(USER_ID)
                    .type(ImMessage.TYPE_TEXT).content("reply").createTime(new Date()).build());
            when(sysUserMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Alice"));

            SendMessageDTO dto = new SendMessageDTO();
            dto.setMsgType("text");
            dto.setContent("reply");
            MessageVO vo = service.quoteMessage(USER_ID, CONV_ID, 12L, dto);
            assertEquals("reply", vo.getContent());
            verify(messageMapper).update(any(ImMessage.class));
        }

        @Test
        @DisplayName("uploadChatFile 成功")
        void uploadChatFileOk() {
            stubMember(USER_ID, CONV_ID);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.txt", "text/plain", "hello".getBytes());
            when(fileStorageService.uploadFile(eq(file), isNull())).thenReturn("2026/a.txt");

            ChatFileUploadVO vo = service.uploadChatFile(USER_ID, CONV_ID, file);
            assertEquals("2026/a.txt", vo.getFileKey());
            verify(objectKeyOwnershipService).claim(USER_ID, "2026/a.txt");
        }

        @Test
        @DisplayName("分片 uploadPart / list / complete / abort")
        void multipartChainOk() throws Exception {
            stubMember(USER_ID, CONV_ID);
            when(valueOps.get(startsWith("linkx:mp:owner:"))).thenReturn(USER_ID + ":" + CONV_ID);
            MockMultipartFile part = new MockMultipartFile("p", "p.bin", "application/octet-stream", new byte[]{1, 2});
            when(fileStorageService.uploadPart(eq("obj"), eq("up-1"), eq(1), any(), eq(2L))).thenReturn("etag-1");
            when(fileStorageService.listUploadedParts("up-1")).thenReturn(
                    List.of(new FileStorageService.PartETag(1, "etag-1"))
            );
            when(fileStorageService.completeMultipartUpload(eq("obj"), eq("up-1"), anyList())).thenReturn("obj");

            assertEquals("etag-1", service.uploadPart(USER_ID, CONV_ID, "obj", "up-1", 1, part));
            assertEquals(1, service.listUploadedParts(USER_ID, CONV_ID, "up-1").size());

            ChatFileUploadVO done = service.completeMultipartUpload(
                    USER_ID, CONV_ID, "obj", "up-1",
                    List.of(new FileStorageService.PartETag(1, "etag-1")),
                    "f.bin", 2L, "application/octet-stream", "a".repeat(64));
            assertEquals("obj", done.getFileKey());
            verify(objectKeyOwnershipService).claim(USER_ID, "obj");
            verify(fileStorageService).saveContentHash(eq("a".repeat(64)), eq("obj"));

            service.abortMultipartUpload(USER_ID, CONV_ID, "obj", "up-1");
            verify(fileStorageService).abortMultipartUpload("obj", "up-1");
        }

        @Test
        @DisplayName("resolveFileByHash 秒传命中")
        void resolveFileByHashOk() {
            when(fileStorageService.getObjectKeyByHashInternal(anyString())).thenReturn("k1");
            when(objectKeyOwnershipService.isOwned(USER_ID, "k1")).thenReturn(true);
            ChatFileUploadVO vo = service.resolveFileByHash(USER_ID, "b".repeat(64), "n.jpg", 10L, "image/jpeg");
            assertNotNull(vo);
            assertEquals("k1", vo.getFileKey());
        }
    }
}
