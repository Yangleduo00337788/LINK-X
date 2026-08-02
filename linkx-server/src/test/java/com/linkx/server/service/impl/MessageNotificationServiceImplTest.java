package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.MessageNotificationVO;
import com.linkx.server.entity.MessageNotification;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.MessageNotificationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MediaUrlService;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MessageNotificationServiceImpl 通知")
class MessageNotificationServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock MessageNotificationMapper notificationMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MediaUrlService mediaUrlService;

    private MessageNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> {
            Object a = inv.getArgument(0);
            return a == null ? null : "https://cdn/" + a;
        });
        service = new MessageNotificationServiceImpl(notificationMapper, sysUserMapper, mediaUrlService);
    }

    private MessageNotification note(long id, long userId, int read) {
        return MessageNotification.builder()
                .id(id).userId(userId).senderId(20L).senderName("Bob").senderAvatar("av")
                .type("moments_like").content("liked").relatedId(1L)
                .readStatus(read).createTime(new Date()).build();
    }

    @Nested
    @DisplayName("列表与未读")
    class ListOps {
        @Test
        @DisplayName("listUnread / listAll / mentions")
        void lists() {
            when(notificationMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(note(1L, USER_ID, 0)));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(20L).nickname("Bob").avatar("av").build()
            ));

            List<MessageNotificationVO> unread = service.listUnread(USER_ID);
            assertEquals(1, unread.size());
            assertEquals("Bob", unread.get(0).getSenderName());

            assertEquals(1, service.listAll(USER_ID).size());
            assertEquals(1, service.listMineMentions(USER_ID, true).size());
            assertEquals(1, service.listMineMentions(USER_ID, false).size());
        }

        @Test
        @DisplayName("getUnreadCount")
        void unreadCount() {
            when(notificationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);
            assertEquals(3, service.getUnreadCount(USER_ID));
            when(notificationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
            assertEquals(0, service.getUnreadCount(USER_ID));
        }
    }

    @Nested
    @DisplayName("已读")
    class ReadOps {
        @Test
        @DisplayName("markAsRead 成功")
        void markOne() {
            MessageNotification n = note(1L, USER_ID, 0);
            when(notificationMapper.selectOneById(1L)).thenReturn(n);
            service.markAsRead(USER_ID, 1L);
            assertEquals(1, n.getReadStatus());
            verify(notificationMapper).update(n);
        }

        @Test
        @DisplayName("markAsRead 无权 404")
        void markForbidden() {
            when(notificationMapper.selectOneById(1L)).thenReturn(note(1L, 99L, 0));
            assertThrows(CustomException.class, () -> service.markAsRead(USER_ID, 1L));
        }

        @Test
        @DisplayName("markAllAsRead")
        void markAll() {
            service.markAllAsRead(USER_ID);
            verify(notificationMapper).updateByQuery(any(MessageNotification.class), any(QueryWrapper.class));
        }

        @Test
        @DisplayName("markAsRead 已读跳过")
        void markAlreadyRead() {
            MessageNotification n = note(1L, USER_ID, 1);
            when(notificationMapper.selectOneById(1L)).thenReturn(n);
            service.markAsRead(USER_ID, 1L);
            verify(notificationMapper).update(n);
        }
    }

    @Nested
    @DisplayName("批量与清理")
    class BulkOps {
        @Test
        @DisplayName("clearAll 清空全部")
        void clearAll() {
            when(notificationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(4L);
            assertEquals(4, service.clearAll(USER_ID));
            verify(notificationMapper).deleteByQuery(any(QueryWrapper.class));
        }

        @Test
        @DisplayName("clearAll null 用户返回 0")
        void clearAllNullUser() {
            assertEquals(0, service.clearAll(null));
        }

        @Test
        @DisplayName("createForUsers 批量创建")
        void createForUsers() {
            when(notificationMapper.insertBatch(anyList())).thenReturn(2);
            int n = service.createForUsers(new ArrayList<>(List.of(10L, 11L, 10L)), 20L, null, null,
                    "system_notice", 1L, "hello");
            assertEquals(2, n);
            verify(notificationMapper).insertBatch(anyList());
        }

        @Test
        @DisplayName("deleteByTypeAndRelatedId")
        void deleteByType() {
            when(notificationMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
            assertEquals(2, service.deleteByTypeAndRelatedId("group_join_request", 100L));
            verify(notificationMapper).deleteByQuery(any(QueryWrapper.class));
            assertEquals(0, service.deleteByTypeAndRelatedId("", 100L));
        }
    }

    @Nested
    @DisplayName("resolveCategory")
    class Category {
        @Test
        @DisplayName("分类映射")
        void categories() {
            assertEquals("moments", MessageNotificationServiceImpl.resolveCategory("moments_like"));
            assertEquals("system", MessageNotificationServiceImpl.resolveCategory("calendar_remind"));
            assertEquals("social", MessageNotificationServiceImpl.resolveCategory("friend_request"));
            assertEquals("other", MessageNotificationServiceImpl.resolveCategory("custom"));
            assertEquals("other", MessageNotificationServiceImpl.resolveCategory(null));
        }

        @Test
        @DisplayName("缺头像批量补全")
        void missingAvatarBatchLoad() {
            MessageNotification n = MessageNotification.builder()
                    .id(8L).userId(USER_ID).senderId(20L).senderName("Bob")
                    .type("moments_mention").content("@you").readStatus(0).createTime(new Date()).build();
            when(notificationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(n));
            when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    SysUser.builder().id(20L).nickname("Bob").avatar("av20").build()
            ));
            List<MessageNotificationVO> list = service.listUnread(USER_ID);
            assertEquals(1, list.size());
            assertTrue(list.get(0).getSenderAvatar().contains("av20"));
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("创建通知")
        void createOk() {
            when(notificationMapper.insert(any(MessageNotification.class))).thenAnswer(inv -> {
                MessageNotification n = inv.getArgument(0);
                n.setId(50L);
                return 1;
            });
            service.create(USER_ID, 20L, "Bob", "av", "friend_request", 7L, "hi");
            verify(notificationMapper).insert(argThat(n ->
                    n.getUserId().equals(USER_ID)
                            && "friend_request".equals(n.getType())
                            && n.getReadStatus() == 0));
        }

        @Test
        @DisplayName("创建时从发送者补全信息")
        void createResolvesSender() {
            when(sysUserMapper.selectOneById(20L)).thenReturn(
                    SysUser.builder().id(20L).username("bob").nickname("BobNick").avatar("av2").build()
            );
            service.create(USER_ID, 20L, null, null, "moments_like", 1L, "liked");
            verify(notificationMapper).insert(argThat(n ->
                    "BobNick".equals(n.getSenderName()) && "av2".equals(n.getSenderAvatar())));
        }
    }

    @Nested
    @DisplayName("delete / getById")
    class Delete {
        @Test
        @DisplayName("delete 成功")
        void deleteOk() {
            MessageNotification n = note(2L, USER_ID, 0);
            when(notificationMapper.selectOneById(2L)).thenReturn(n);
            service.delete(USER_ID, 2L);
            verify(notificationMapper).deleteById(2L);
        }

        @Test
        @DisplayName("getById 不存在")
        void notFound() {
            when(notificationMapper.selectOneById(999L)).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.delete(USER_ID, 999L));
            assertEquals(404, ex.getCode());
            verify(notificationMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("delete 非本人通知")
        void deleteForbidden() {
            when(notificationMapper.selectOneById(3L)).thenReturn(note(3L, 99L, 0));
            assertThrows(CustomException.class, () -> service.delete(USER_ID, 3L));
        }
    }
}
