package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.dto.UpdateMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.entity.*;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.*;
import com.linkx.server.service.ExternalMediaProxyService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminReviewService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MomentsServiceImpl 朋友圈服务")
class MomentsServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long FRIEND_ID = 20L;
    private static final long STRANGER_ID = 30L;
    private static final long AT_USER_ID = 40L;
    private static final long POST_ID = 100L;
    private static final long COMMENT_ID = 200L;
    private static final long PARENT_COMMENT_ID = 201L;

    private static final byte[] VALID_PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Mock MomentsPostMapper postMapper;
    @Mock MomentsImageMapper imageMapper;
    @Mock MomentsLikeMapper likeMapper;
    @Mock MomentsCommentMapper commentMapper;
    @Mock SysUserMapper userMapper;
    @Mock SysUserRelationMapper sysUserRelationMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MediaUrlService mediaUrlService;
    @Mock ExternalMediaProxyService externalMediaProxyService;
    @Mock ObjectKeyOwnershipService objectKeyOwnershipService;
    @Mock MessageNotificationService notificationService;
    @Mock MessageNotificationMapper notificationMapper;
    @Mock ImMessagePushService imPushService;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ObjectProvider<AdminReviewService> adminReviewService;
    @Mock AdminReviewService reviewService;

    private ObjectMapper objectMapper;
    private MomentsServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(adminReviewService.getIfAvailable()).thenReturn(null);
        when(sensitiveWordService.filter(anyString())).thenAnswer(inv ->
                new SensitiveWordService.FilterResult(inv.getArgument(0), false, false, false, List.of()));
        when(mediaUrlService.resolve(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg == null ? null : "https://cdn/" + arg;
        });
        when(mediaUrlService.isExternalHttpUrl(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return url != null && url.trim().startsWith("https://external");
        });
        when(externalMediaProxyService.wrapExternalUrl(anyString())).thenAnswer(inv ->
                "https://proxy/" + inv.getArgument(0));
        when(fileStorageService.extractObjectKey(anyString())).thenAnswer(inv -> inv.getArgument(0));

        doAnswer(inv -> {
            MomentsPost post = inv.getArgument(0);
            if (post.getId() == null) {
                post.setId(POST_ID);
            }
            if (post.getCreateTime() == null) {
                post.setCreateTime(new Date());
            }
            return 1;
        }).when(postMapper).insert(any(MomentsPost.class));

        doAnswer(inv -> {
            MomentsImage image = inv.getArgument(0);
            if (image.getId() == null) {
                image.setId(1L);
            }
            return 1;
        }).when(imageMapper).insert(any(MomentsImage.class));

        doAnswer(inv -> {
            MomentsComment comment = inv.getArgument(0);
            if (comment.getId() == null) {
                comment.setId(COMMENT_ID);
            }
            if (comment.getCreateTime() == null) {
                comment.setCreateTime(new Date());
            }
            return 1;
        }).when(commentMapper).insert(any(MomentsComment.class));

        doAnswer(inv -> 1).when(likeMapper).insert(any(MomentsLike.class));

        when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(imageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(likeMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(commentMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        service = new MomentsServiceImpl(
                postMapper, imageMapper, likeMapper, commentMapper, userMapper, sysUserRelationMapper,
                fileStorageService, mediaUrlService, externalMediaProxyService, objectKeyOwnershipService,
                notificationService, notificationMapper, imPushService, objectMapper,
                sensitiveWordService, adminReviewService);
    }

    private SysUser user(long id, String nickname) {
        return SysUser.builder().id(id).username("u" + id).nickname(nickname).avatar("av" + id).build();
    }

    private MomentsPost post(long id, long userId, String content, Integer visibility) {
        return MomentsPost.builder()
                .id(id)
                .userId(userId)
                .content(content)
                .visibility(visibility)
                .deleted(0)
                .createTime(new Date())
                .build();
    }

    private SysUserRelation relation(long userId, long friendId) {
        return SysUserRelation.builder()
                .userId(userId)
                .friendId(friendId)
                .status(1)
                .deleted(0)
                .build();
    }

    private void stubFriends(long userId, Long... friendIds) {
        List<SysUserRelation> relations = new ArrayList<>();
        for (Long fid : friendIds) {
            relations.add(relation(userId, fid));
        }
        when(sysUserRelationMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(relations);
    }

    private void stubPostForInteract(long postId, long authorId, Integer visibility) {
        when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(post(postId, authorId, "hello", visibility));
    }

    @Nested
    @DisplayName("publish 发布动态")
    class PublishTests {

        @Test
        @DisplayName("用户不存在返回 404")
        void userNotFound() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(null);
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("hi");
            CustomException ex = assertThrows(CustomException.class, () -> service.publish(USER_ID, dto));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("内容与媒体均为空返回 400")
        void emptyContentAndImages() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            CustomException ex = assertThrows(CustomException.class, () -> service.publish(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("纯文本发布成功")
        void textOnly() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("  hello world  ");

            MomentsPostVO vo = service.publish(USER_ID, dto);

            assertEquals(POST_ID, vo.getId());
            assertEquals("hello world", vo.getContent());
            verify(postMapper).insert(any(MomentsPost.class));
        }

        @Test
        @DisplayName("带图片与可见性发布")
        void withImagesAndVisibility() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("pic");
            dto.setImages(List.of("2026/a.png", "", "  "));
            dto.setVisibility(1);
            dto.setLocation("  Shanghai  ");

            MomentsPostVO vo = service.publish(USER_ID, dto);

            assertEquals(1, vo.getVisibility());
            assertEquals("  Shanghai  ", vo.getLocation());
            verify(imageMapper, times(1)).insert(any(MomentsImage.class));
            verify(objectKeyOwnershipService).assertOwned(USER_ID, "2026/a.png");
        }

        @Test
        @DisplayName("外链图片走 SafeExternalUrl 校验")
        void externalImageUrl() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setImages(List.of("https://external.example.com/a.jpg"));

            // DNS 不可解析时拒绝；本地 object key 路径才会 claim
            assertThrows(CustomException.class, () -> service.publish(USER_ID, dto));
            verify(objectKeyOwnershipService, never()).assertOwned(anyLong(), anyString());
        }

        @Test
        @DisplayName("无效 object key 拒绝")
        void invalidObjectKey() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(fileStorageService.extractObjectKey("../bad")).thenReturn("../bad");
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setImages(List.of("../bad"));

            CustomException ex = assertThrows(CustomException.class, () -> service.publish(USER_ID, dto));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("敏感词拦截 blocked")
        void blockedSensitive() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("bad", false, true, false, List.of("违规")));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("bad word");

            CustomException ex = assertThrows(CustomException.class, () -> service.publish(USER_ID, dto));
            assertEquals(400, ex.getCode());
            verify(postMapper, never()).insert(any(MomentsPost.class));
        }

        @Test
        @DisplayName("敏感词 filtered 入审")
        void filteredSensitiveEnqueuesReview() {
            when(adminReviewService.getIfAvailable()).thenReturn(reviewService);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("clean", true, false, false, List.of("词")));

            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("dirty");

            service.publish(USER_ID, dto);

            verify(reviewService).createFromSensitiveHit(
                    eq(USER_ID), eq("moment"), eq(String.valueOf(POST_ID)), isNull(),
                    eq("dirty"), eq("词"), eq("filtered"));
        }

        @Test
        @DisplayName("敏感词 alert 入审")
        void alertedSensitiveEnqueuesReview() {
            when(adminReviewService.getIfAvailable()).thenReturn(reviewService);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("text", false, false, true, List.of("alert词")));

            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("alert");

            service.publish(USER_ID, dto);

            verify(reviewService).createFromSensitiveHit(
                    eq(USER_ID), eq("moment"), eq(String.valueOf(POST_ID)), isNull(),
                    eq("alert"), eq("alert词"), eq("alert"));
        }

        @Test
        @DisplayName("@提醒好友并推送")
        void atUsersNotify() {
            stubFriends(USER_ID, FRIEND_ID, AT_USER_ID);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("hey");
            dto.setAtUsers(new ArrayList<>(Arrays.asList(AT_USER_ID, USER_ID, null, AT_USER_ID)));

            service.publish(USER_ID, dto);

            verify(notificationService).create(
                    eq(AT_USER_ID), eq(USER_ID), eq("Me"), any(), eq("moments_at"), eq(POST_ID), anyString());
            verify(imPushService).pushToUser(eq(AT_USER_ID), eq("notification_refresh"), any());
            verify(imPushService).pushToUser(eq(FRIEND_ID), eq("moments_new_post"), any());
        }

        @Test
        @DisplayName("通知失败不影响发布")
        void notificationFailureIgnored() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            doThrow(new RuntimeException("notify fail")).when(notificationService)
                    .create(anyLong(), anyLong(), any(), any(), anyString(), anyLong(), anyString());
            PublishMomentsDTO dto = new PublishMomentsDTO();
            dto.setContent("x");
            dto.setAtUsers(List.of(AT_USER_ID));

            assertDoesNotThrow(() -> service.publish(USER_ID, dto));
        }
    }

    @Nested
    @DisplayName("list 动态列表")
    class ListTests {

        @Test
        @DisplayName("空列表")
        void empty() {
            List<MomentsPostVO> result = service.list(USER_ID, null, null, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("含点赞评论与图片")
        void withPosts() {
            stubFriends(USER_ID, FRIEND_ID);
            MomentsPost p = post(POST_ID, FRIEND_ID, "content", 0);
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));
            when(imageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    MomentsImage.builder().postId(POST_ID).url("img/key.png").sortOrder(0).build()
            ));
            when(likeMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    MomentsLike.builder().postId(POST_ID).userId(USER_ID).build()
            ));
            when(commentMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    MomentsComment.builder().id(COMMENT_ID).postId(POST_ID).userId(FRIEND_ID)
                            .content("c").createTime(new Date()).build()
            ));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    user(FRIEND_ID, "Friend"), user(USER_ID, "Me")
            ));

            List<MomentsPostVO> result = service.list(USER_ID, POST_ID + 1, 100, "cont%ent_");

            assertEquals(1, result.size());
            assertTrue(result.get(0).isLiked());
            assertEquals(1, result.get(0).getLikes());
            assertEquals(1, result.get(0).getComments().size());
            assertFalse(result.get(0).getImages().isEmpty());
        }

        @Test
        @DisplayName("仅好友可见动态对非好友过滤")
        void friendsOnlyFiltered() {
            MomentsPost p = post(POST_ID, STRANGER_ID, "secret", 1);
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(STRANGER_ID, "S")));

            List<MomentsPostVO> result = service.list(USER_ID, null, 20, null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("listByUser 用户主页")
    class ListByUserTests {

        @Test
        @DisplayName("空列表")
        void empty() {
            when(userMapper.selectOneById(FRIEND_ID)).thenReturn(user(FRIEND_ID, "F"));
            assertTrue(service.listByUser(USER_ID, FRIEND_ID, null, null, null).isEmpty());
        }

        @Test
        @DisplayName("本人可见私密动态")
        void selfSeesPrivate() {
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            MomentsPost p = post(POST_ID, USER_ID, "private", 2);
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));

            List<MomentsPostVO> result = service.listByUser(USER_ID, USER_ID, null, 20, null);

            assertEquals(1, result.size());
            assertEquals(2, result.get(0).getVisibility());
        }

        @Test
        @DisplayName("好友可见仅好友动态")
        void friendSeesFriendsOnly() {
            stubFriends(USER_ID, FRIEND_ID);
            when(userMapper.selectOneById(FRIEND_ID)).thenReturn(user(FRIEND_ID, "F"));
            MomentsPost p = post(POST_ID, FRIEND_ID, "friends", 1);
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(FRIEND_ID, "F")));

            List<MomentsPostVO> result = service.listByUser(USER_ID, FRIEND_ID, null, 20, null);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("陌生人不可见仅好友动态")
        void strangerCannotSeeFriendsOnly() {
            when(userMapper.selectOneById(FRIEND_ID)).thenReturn(user(FRIEND_ID, "F"));
            MomentsPost p = post(POST_ID, FRIEND_ID, "friends", 1);
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));

            List<MomentsPostVO> result = service.listByUser(STRANGER_ID, FRIEND_ID, null, 20, null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("解析 @用户昵称")
        void resolvesAtUserNames() throws Exception {
            when(userMapper.selectOneById(FRIEND_ID)).thenReturn(user(FRIEND_ID, "F"));
            MomentsPost p = post(POST_ID, FRIEND_ID, "at", 0);
            p.setAtUsers(objectMapper.writeValueAsString(List.of(AT_USER_ID)));
            when(postMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(p));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    user(FRIEND_ID, "F"), user(AT_USER_ID, "AtUser")
            ));

            List<MomentsPostVO> result = service.listByUser(USER_ID, FRIEND_ID, null, 20, null);

            assertEquals(1, result.get(0).getAtUserNames().size());
            assertEquals("AtUser", result.get(0).getAtUserNames().get(0));
        }
    }

    @Nested
    @DisplayName("update 编辑动态")
    class UpdateTests {

        @Test
        @DisplayName("动态不存在")
        void notFound() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            UpdateMomentsDTO dto = new UpdateMomentsDTO();
            dto.setContent("x");
            CustomException ex = assertThrows(CustomException.class, () -> service.update(USER_ID, POST_ID, dto));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("非作者拒绝")
        void notOwner() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, FRIEND_ID, "x", 0));
            UpdateMomentsDTO dto = new UpdateMomentsDTO();
            dto.setContent("y");
            CustomException ex = assertThrows(CustomException.class, () -> service.update(USER_ID, POST_ID, dto));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("无可更新字段")
        void nothingToUpdate() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, USER_ID, "x", 0));
            assertThrows(CustomException.class, () -> service.update(USER_ID, POST_ID, new UpdateMomentsDTO()));
        }

        @Test
        @DisplayName("更新内容与图片")
        void updateContentAndImages() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, USER_ID, "old", 0));
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));

            UpdateMomentsDTO dto = new UpdateMomentsDTO();
            dto.setContent("new");
            dto.setLocation("  ");
            dto.setVisibility(2);
            dto.setAtUsers(List.of(FRIEND_ID));
            dto.setImages(List.of("2026/new.png"));

            MomentsPostVO vo = service.update(USER_ID, POST_ID, dto);

            assertEquals("new", vo.getContent());
            assertNull(vo.getLocation());
            assertEquals(2, vo.getVisibility());
            verify(imageMapper).deleteByQuery(any(QueryWrapper.class));
            verify(postMapper).update(any(MomentsPost.class));
        }

        @Test
        @DisplayName("编辑时敏感词 blocked 入审")
        void blockedOnUpdate() {
            when(adminReviewService.getIfAvailable()).thenReturn(reviewService);
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, USER_ID, "old", 0));
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("bad", false, true, false, List.of("x")));

            UpdateMomentsDTO dto = new UpdateMomentsDTO();
            dto.setContent("bad");

            assertThrows(CustomException.class, () -> service.update(USER_ID, POST_ID, dto));
            verify(reviewService).createFromSensitiveHit(
                    eq(USER_ID), eq("moment"), eq(String.valueOf(POST_ID)), isNull(),
                    eq("bad"), eq("x"), eq("blocked"));
        }
    }

    @Nested
    @DisplayName("delete 删除动态")
    class DeleteTests {

        @Test
        @DisplayName("不存在")
        void notFound() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.delete(USER_ID, POST_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("非作者拒绝")
        void notOwner() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, FRIEND_ID, "x", 0));
            CustomException ex = assertThrows(CustomException.class, () -> service.delete(USER_ID, POST_ID));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("删除成功并清理资源")
        void success() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, USER_ID, "x", 0));
            when(imageMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                    MomentsImage.builder().url("2026/a.png").build(),
                    MomentsImage.builder().url("https://external/x.jpg").build()
            ));

            service.delete(USER_ID, POST_ID);

            verify(fileStorageService).deleteFile("2026/a.png");
            verify(fileStorageService, never()).deleteFile("https://external/x.jpg");
            verify(notificationMapper).deleteByQuery(any(QueryWrapper.class));
            verify(postMapper).deleteById(POST_ID);
        }
    }

    @Nested
    @DisplayName("adminDeletePost 管理员删帖")
    class AdminDeletePostTests {

        @Test
        @DisplayName("完全不存在返回 404")
        void notFoundAtAll() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(postMapper.selectOneById(POST_ID)).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.adminDeletePost(POST_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("已逻辑删除则静默返回")
        void alreadyDeleted() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(postMapper.selectOneById(POST_ID)).thenReturn(post(POST_ID, USER_ID, "x", 0));

            assertDoesNotThrow(() -> service.adminDeletePost(POST_ID));
            verify(postMapper, never()).deleteById(POST_ID);
        }

        @Test
        @DisplayName("存在则强制删除")
        void forceDelete() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(post(POST_ID, USER_ID, "x", 0));

            service.adminDeletePost(POST_ID);

            verify(postMapper).deleteById(POST_ID);
        }
    }

    @Nested
    @DisplayName("like / unlike 点赞")
    class LikeTests {

        @Test
        @DisplayName("动态不存在")
        void postNotFound() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.like(USER_ID, POST_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("无权查看私密动态")
        void noPermission() {
            stubPostForInteract(POST_ID, FRIEND_ID, 2);
            CustomException ex = assertThrows(CustomException.class, () -> service.like(USER_ID, POST_ID));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("已点赞则幂等返回")
        void alreadyLiked() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(likeMapper.selectOneByQuery(any(QueryWrapper.class)))
                    .thenReturn(MomentsLike.builder().postId(POST_ID).userId(USER_ID).build());

            service.like(USER_ID, POST_ID);

            verify(likeMapper, never()).insert(any(MomentsLike.class));
        }

        @Test
        @DisplayName("点赞成功并通知作者")
        void likeSuccessNotifiesAuthor() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(likeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Liker"));

            service.like(USER_ID, POST_ID);

            verify(likeMapper).insert(any(MomentsLike.class));
            verify(notificationService).create(
                    eq(FRIEND_ID), eq(USER_ID), eq("Liker"), any(), eq("moments_like"), eq(POST_ID), anyString());
        }

        @Test
        @DisplayName("赞自己不发通知")
        void likeSelfNoNotify() {
            stubPostForInteract(POST_ID, USER_ID, 0);
            when(likeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

            service.like(USER_ID, POST_ID);

            verify(notificationService, never()).create(anyLong(), anyLong(), any(), any(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("并发唯一键冲突静默返回")
        void duplicateKeyIgnored() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(likeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            doThrow(new DuplicateKeyException("dup")).when(likeMapper).insert(any(MomentsLike.class));

            assertDoesNotThrow(() -> service.like(USER_ID, POST_ID));
        }

        @Test
        @DisplayName("取消点赞")
        void unlike() {
            service.unlike(USER_ID, POST_ID);
            verify(likeMapper).deleteByQuery(any(QueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("comment 评论")
    class CommentTests {

        @Test
        @DisplayName("动态不存在")
        void postNotFound() {
            when(postMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("c");
            CustomException ex = assertThrows(CustomException.class, () -> service.comment(USER_ID, POST_ID, dto));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("评论者不存在")
        void userNotFound() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(null);
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("c");
            CustomException ex = assertThrows(CustomException.class, () -> service.comment(USER_ID, POST_ID, dto));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("父评论不存在")
        void parentNotFound() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(commentMapper.selectOneById(PARENT_COMMENT_ID)).thenReturn(null);
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("reply");
            dto.setParentId(PARENT_COMMENT_ID);
            CustomException ex = assertThrows(CustomException.class, () -> service.comment(USER_ID, POST_ID, dto));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("父评论不属于该动态")
        void parentWrongPost() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(commentMapper.selectOneById(PARENT_COMMENT_ID)).thenReturn(
                    MomentsComment.builder().id(PARENT_COMMENT_ID).postId(999L).build());
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("reply");
            dto.setParentId(PARENT_COMMENT_ID);
            CustomException ex = assertThrows(CustomException.class, () -> service.comment(USER_ID, POST_ID, dto));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("评论成功含 mentions 通知")
        void commentSuccess() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("nice");
            dto.setMentions(List.of(AT_USER_ID));

            MomentsCommentVO vo = service.comment(USER_ID, POST_ID, dto);

            assertEquals(COMMENT_ID, vo.getId());
            assertEquals(List.of(AT_USER_ID), vo.getMentions());
            verify(notificationService).create(
                    eq(FRIEND_ID), eq(USER_ID), eq("Me"), any(), eq("moments_comment"), eq(POST_ID), anyString());
            verify(notificationService).create(
                    eq(AT_USER_ID), eq(USER_ID), eq("Me"), any(), eq("moments_mention"), eq(POST_ID), anyString());
        }

        @Test
        @DisplayName("从内容解析 @昵称")
        void parseMentionFromContent() {
            stubFriends(USER_ID, FRIEND_ID);
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(userMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(user(FRIEND_ID, "Bob")));

            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("hi @Bob");

            MomentsCommentVO vo = service.comment(USER_ID, POST_ID, dto);

            assertEquals(List.of(FRIEND_ID), vo.getMentions());
        }

        @Test
        @DisplayName("评论敏感词 blocked")
        void blockedComment() {
            stubPostForInteract(POST_ID, FRIEND_ID, 0);
            when(userMapper.selectOneById(USER_ID)).thenReturn(user(USER_ID, "Me"));
            when(sensitiveWordService.filter(anyString())).thenReturn(
                    new SensitiveWordService.FilterResult("bad", false, true, false, List.of("bad")));
            CommentMomentsDTO dto = new CommentMomentsDTO();
            dto.setContent("bad");
            assertThrows(CustomException.class, () -> service.comment(USER_ID, POST_ID, dto));
        }
    }

    @Nested
    @DisplayName("deleteComment 删评论")
    class DeleteCommentTests {

        @Test
        @DisplayName("评论不存在")
        void notFound() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class, () -> service.deleteComment(USER_ID, COMMENT_ID));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("无权删除")
        void forbidden() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(
                    MomentsComment.builder().id(COMMENT_ID).userId(FRIEND_ID).postId(POST_ID).build());
            when(postMapper.selectOneById(POST_ID)).thenReturn(post(POST_ID, STRANGER_ID, "x", 0));
            CustomException ex = assertThrows(CustomException.class, () -> service.deleteComment(USER_ID, COMMENT_ID));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("评论作者可删")
        void authorCanDelete() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(
                    MomentsComment.builder().id(COMMENT_ID).userId(USER_ID).postId(POST_ID).build());
            service.deleteComment(USER_ID, COMMENT_ID);
            verify(commentMapper).deleteById(COMMENT_ID);
        }

        @Test
        @DisplayName("动态作者可删他人评论")
        void postOwnerCanDelete() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(
                    MomentsComment.builder().id(COMMENT_ID).userId(FRIEND_ID).postId(POST_ID).build());
            when(postMapper.selectOneById(POST_ID)).thenReturn(post(POST_ID, USER_ID, "x", 0));
            service.deleteComment(USER_ID, COMMENT_ID);
            verify(commentMapper).deleteById(COMMENT_ID);
        }
    }

    @Nested
    @DisplayName("adminDeleteComment 管理员删评")
    class AdminDeleteCommentTests {

        @Test
        @DisplayName("不存在")
        void notFound() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(null);
            assertThrows(CustomException.class, () -> service.adminDeleteComment(COMMENT_ID));
        }

        @Test
        @DisplayName("成功删除")
        void success() {
            when(commentMapper.selectOneById(COMMENT_ID)).thenReturn(
                    MomentsComment.builder().id(COMMENT_ID).build());
            service.adminDeleteComment(COMMENT_ID);
            verify(commentMapper).deleteById(COMMENT_ID);
        }
    }

    @Nested
    @DisplayName("uploadImage 上传媒体")
    class UploadImageTests {

        @Test
        @DisplayName("空文件")
        void emptyFile() {
            CustomException ex = assertThrows(CustomException.class, () -> service.uploadImage(USER_ID, null));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("非法类型")
        void invalidType() {
            MockMultipartFile file = new MockMultipartFile(
                    "f", "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
            CustomException ex = assertThrows(CustomException.class, () -> service.uploadImage(USER_ID, file));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("图片 magic 无效")
        void invalidImageMagic() {
            MockMultipartFile file = new MockMultipartFile(
                    "f", "a.png", "image/png", "not-png".getBytes(StandardCharsets.UTF_8));
            CustomException ex = assertThrows(CustomException.class, () -> service.uploadImage(USER_ID, file));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("图片上传成功")
        void imageSuccess() throws Exception {
            MockMultipartFile file = new MockMultipartFile("f", "a.png", "image/png", VALID_PNG);
            when(fileStorageService.uploadFile(file)).thenReturn("2026/a.png");

            String key = service.uploadImage(USER_ID, file);

            assertEquals("2026/a.png", key);
            verify(objectKeyOwnershipService).claim(USER_ID, "2026/a.png");
        }

        @Test
        @DisplayName("视频上传跳过图片校验")
        void videoSuccess() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "f", "a.mp4", "video/mp4", "bytes".getBytes(StandardCharsets.UTF_8));
            when(fileStorageService.uploadFile(file)).thenReturn("2026/a.mp4");

            String key = service.uploadImage(USER_ID, file);

            assertEquals("2026/a.mp4", key);
        }

        @Test
        @DisplayName("上传失败返回 500")
        void uploadFailure() {
            MockMultipartFile file = new MockMultipartFile("f", "a.png", "image/png", VALID_PNG);
            when(fileStorageService.uploadFile(file)).thenThrow(new RuntimeException("io"));
            CustomException ex = assertThrows(CustomException.class, () -> service.uploadImage(USER_ID, file));
            assertEquals(500, ex.getCode());
        }
    }
}
