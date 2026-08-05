package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminReviewBatchDTO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FavoriteService;
import com.linkx.server.service.GroupAnnouncementService;
import com.linkx.server.service.GroupAssetService;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.MomentsService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.admin.AdminAudienceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.impl.AdminReviewServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminReviewService 审核任务")
class AdminReviewServiceTest {

    @Mock SysReviewTaskMapper reviewTaskMapper;
    @Mock FeedbackMapper feedbackMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MessageNotificationService notificationService;
    @Mock ImMessagePushService imPushService;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock AdminAudienceService adminAudienceService;
    @Mock MediaUrlService mediaUrlService;
    @Mock AdminUserService adminUserService;
    @Mock RbacService rbacService;
    @Mock ChatService chatService;
    @Mock MomentsService momentsService;
    @Mock GroupAnnouncementService groupAnnouncementService;
    @Mock GroupAssetService groupAssetService;
    @Mock FavoriteService favoriteService;
    @Mock GroupService groupService;
    @Mock ImConversationMapper conversationMapper;
    @Mock GroupAssetMapper groupAssetMapper;
    @Mock FavoriteMapper favoriteMapper;
    @Mock LinkxProperties linkxProperties;
    @Mock ReviewRiskScoringService reviewRiskScoringService;
    @Mock com.linkx.server.service.admin.approval.ApprovalFlowEngine approvalFlowEngine;
    @Mock com.linkx.server.mapper.admin.SysApprovalInstanceMapper approvalInstanceMapper;

    private AdminReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(linkxProperties.getApp()).thenReturn(new LinkxProperties.App());
        lenient().when(reviewRiskScoringService.elevateLevel(any(), any())).thenAnswer(inv ->
                inv.getArgument(0, String.class));
        service = new AdminReviewServiceImpl(
                reviewTaskMapper, feedbackMapper, sysUserMapper, notificationService, imPushService,
                adminEventPublisher, adminAudienceService, mediaUrlService, adminUserService, rbacService, chatService,
                momentsService, groupAnnouncementService, groupAssetService, favoriteService,
                groupService, conversationMapper, groupAssetMapper, favoriteMapper, linkxProperties,
                reviewRiskScoringService, approvalFlowEngine, approvalInstanceMapper);
    }

    private SysReviewTask pending(Long id) {
        return pending(id, SysReviewTask.TARGET_MESSAGE, "55");
    }

    private SysReviewTask pending(Long id, String targetType, String targetId) {
        return SysReviewTask.builder()
                .id(id)
                .sourceType(SysReviewTask.SOURCE_SENSITIVE)
                .targetType(targetType)
                .targetId(targetId)
                .title("敏感词命中")
                .contentSnapshot("用户ID: 100\n会话ID: 9\n内容: hi")
                .status(SysReviewTask.STATUS_PENDING)
                .reporterUserId(200L)
                .reporterUsername("bob")
                .riskLevel("medium")
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private AdminReviewResolveDTO deleteResolve() {
        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("违规");
        dto.setContentAction("delete");
        dto.setUserAction("none");
        return dto;
    }

    @Test
    @DisplayName("列表/导出/详情/计数")
    void list_export_detail_count() {
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(reviewTaskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(reviewTaskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(pending(1L)));

        AdminReviewQueryDTO q = new AdminReviewQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("敏感");
        q.setReviewStatus(SysReviewTask.STATUS_PENDING);
        q.setSourceType(SysReviewTask.SOURCE_SENSITIVE);
        q.setTargetType(SysReviewTask.TARGET_MESSAGE);
        q.setRiskLevel("medium");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());

        assertEquals(1, service.list(q).getTotal());
        assertEquals(1, service.listForExport(q).size());

        when(reviewTaskMapper.selectOneById(1L)).thenReturn(pending(1L));
        assertEquals(1L, service.detail(1L).getId());

        assertEquals(1L, service.countPending());
        assertEquals(1L, service.countPendingBySource(SysReviewTask.SOURCE_SENSITIVE));
    }

    @Test
    @DisplayName("通过/驳回（无额外处置）")
    void approve_reject_none() {
        SysReviewTask t1 = pending(10L);
        when(reviewTaskMapper.selectOneById(10L)).thenReturn(t1);

        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("ok");
        dto.setUserAction("none");
        dto.setContentAction("none");
        service.approve(10L, dto, 1L);
        assertEquals(SysReviewTask.STATUS_APPROVED, t1.getStatus());
        verify(reviewTaskMapper).update(t1);
        verify(notificationService).create(eq(200L), eq(1L), anyString(), isNull(),
                eq("review_approved"), any(), anyString());

        SysReviewTask t2 = pending(11L);
        when(reviewTaskMapper.selectOneById(11L)).thenReturn(t2);
        service.reject(11L, dto, 1L);
        assertEquals(SysReviewTask.STATUS_REJECTED, t2.getStatus());
    }

    @Test
    @DisplayName("通过并删除消息内容")
    void approve_delete_message() {
        SysReviewTask task = pending(12L);
        when(reviewTaskMapper.selectOneById(12L)).thenReturn(task);
        when(chatService.adminForceRecallMessage(55L)).thenReturn(MessageVO.builder().id(55L).build());

        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("删");
        dto.setContentAction("delete");
        dto.setUserAction("none");
        service.approve(12L, dto, 2L);

        verify(chatService).adminForceRecallMessage(55L);
        verify(imPushService).pushRecallToConversationMembers(any(MessageVO.class));
        assertTrue(task.getResolution().contains("已撤回消息"));
    }

    @Test
    @DisplayName("批量与非法 action")
    void batch() {
        AdminReviewBatchDTO bad = new AdminReviewBatchDTO();
        bad.setAction("noop");
        bad.setIds(List.of(1L));
        assertThrows(CustomException.class, () -> service.batch(bad, 1L));

        SysReviewTask ok = pending(20L);
        SysReviewTask done = pending(21L);
        done.setStatus(SysReviewTask.STATUS_APPROVED);
        when(reviewTaskMapper.selectOneById(20L)).thenReturn(ok);
        when(reviewTaskMapper.selectOneById(21L)).thenReturn(done);

        AdminReviewBatchDTO dto = new AdminReviewBatchDTO();
        dto.setAction("approve");
        dto.setIds(new ArrayList<>(Arrays.asList(20L, 21L, null)));
        dto.setResolution("batch");
        dto.setContentAction("none");
        dto.setUserAction("none");
        var result = service.batch(dto, 1L);
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
    }

    @Test
    @DisplayName("从举报/敏感词创建任务")
    void create_from_sources() {
        Feedback fb = Feedback.builder()
                .id(3L)
                .userId(9L)
                .username("alice")
                .content("[举报用户]\n用户ID: 88\n证据\n1. evidence/a.png")
                .status("pending")
                .createTime(new Date())
                .build();
        when(reviewTaskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(reviewTaskMapper.insert(any(SysReviewTask.class))).thenAnswer(inv -> {
            ((SysReviewTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(adminAudienceService.reviewOperatorUserIds()).thenReturn(List.of(1L));
        service.createFromReportFeedback(fb);
        verify(adminEventPublisher).publishToUsers(eq("review_created"), eq(100L), eq(List.of(1L)));

        when(sysUserMapper.selectOneById(9L)).thenReturn(
                SysUser.builder().id(9L).username("alice").nickname("A").build());
        when(reviewTaskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        service.createFromSensitiveHit(9L, SysReviewTask.TARGET_CONVERSATION, "77",
                77L, "bad word", "spam", "blocked");
        verify(reviewTaskMapper, atLeast(2)).insert(any(SysReviewTask.class));
    }

    @Test
    @DisplayName("ensureReportTasks 同步未关联举报")
    void ensureReportTasks_insertsMissing() {
        Feedback fb = Feedback.builder()
                .id(5L)
                .userId(1L)
                .username("r")
                .content("[举报群]\n群ID: 42\n说明")
                .status("pending")
                .createTime(new Date())
                .build();
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(fb));
        when(reviewTaskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(reviewTaskMapper.insert(any(SysReviewTask.class))).thenAnswer(inv -> {
            ((SysReviewTask) inv.getArgument(0)).setId(200L);
            return 1;
        });
        when(reviewTaskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);

        service.countPending();
        verify(reviewTaskMapper).insert(argThat(t ->
                SysReviewTask.TARGET_GROUP.equals(t.getTargetType()) && "42".equals(t.getTargetId())));
    }

    @Test
    @DisplayName("详情不存在与证据 URL 解析")
    void detail_notFound_and_evidence() {
        when(reviewTaskMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));

        SysReviewTask withEvidence = pending(30L);
        withEvidence.setContentSnapshot("用户ID: 1\n1. uploads/evidence/a.png\n2. ../bad.png");
        when(reviewTaskMapper.selectOneById(30L)).thenReturn(withEvidence);
        when(mediaUrlService.resolveFile("uploads/evidence/a.png")).thenReturn("https://cdn/a.png");
        var vo = service.detail(30L);
        assertEquals(1, vo.getEvidenceUrls().size());
        assertEquals("https://cdn/a.png", vo.getEvidenceUrls().get(0));
    }

    @Test
    @DisplayName("详情 subjectUserId 来自群文件/收藏")
    void detail_subjectFromAssetAndFavorite() {
        SysReviewTask assetTask = pending(31L, SysReviewTask.TARGET_GROUP_FILE, "501");
        when(reviewTaskMapper.selectOneById(31L)).thenReturn(assetTask);
        when(groupAssetMapper.selectOneById(501L)).thenReturn(
                GroupAsset.builder().id(501L).uploaderId(888L).build());
        assertEquals(888L, service.detail(31L).getSubjectUserId());

        SysReviewTask favTask = pending(32L, SysReviewTask.TARGET_FAVORITE, "601");
        when(reviewTaskMapper.selectOneById(32L)).thenReturn(favTask);
        when(favoriteMapper.selectOneById(601L)).thenReturn(
                Favorite.builder().id(601L).userId(777L).build());
        assertEquals(777L, service.detail(32L).getSubjectUserId());
    }

    @Test
    @DisplayName("通过并删除动态/评论/公告/群文件/收藏")
    void approve_delete_contentTypes() {
        approveDeleteContent(40L, SysReviewTask.TARGET_MOMENT, "10", "已删除动态",
                () -> verify(momentsService).adminDeletePost(10L));
        approveDeleteContent(41L, SysReviewTask.TARGET_MOMENT_COMMENT, "11", "已删除评论",
                () -> verify(momentsService).adminDeleteComment(11L));
        approveDeleteContent(42L, SysReviewTask.TARGET_ANNOUNCEMENT, "12", "已删除公告",
                () -> verify(groupAnnouncementService).adminDelete(12L));
        approveDeleteContent(43L, SysReviewTask.TARGET_GROUP_FILE, "13", "已删除群文件",
                () -> verify(groupAssetService).adminDelete(13L));
        approveDeleteContent(44L, SysReviewTask.TARGET_FAVORITE, "14", "已删除收藏",
                () -> verify(favoriteService).adminDelete(14L));
    }

    private void approveDeleteContent(Long id, String targetType, String targetId,
                                      String resolutionHint, Runnable verifyDelete) {
        SysReviewTask task = pending(id, targetType, targetId);
        when(reviewTaskMapper.selectOneById(id)).thenReturn(task);
        service.approve(id, deleteResolve(), 2L);
        verifyDelete.run();
        assertTrue(task.getResolution().contains(resolutionHint));
    }

    @Test
    @DisplayName("通过并冻结/封禁用户")
    void approve_user_freeze_and_ban() {
        SysReviewTask userTask = pending(50L, SysReviewTask.TARGET_USER, "100");
        when(reviewTaskMapper.selectOneById(50L)).thenReturn(userTask);
        when(rbacService.hasPermission(2L, "admin:user:freeze")).thenReturn(true);

        AdminReviewResolveDTO freezeDto = new AdminReviewResolveDTO();
        freezeDto.setResolution("冻结");
        freezeDto.setUserAction("freeze");
        freezeDto.setContentAction("none");
        service.approve(50L, freezeDto, 2L);
        verify(adminUserService).freeze(eq(100L), any(), eq(2L));
        assertTrue(userTask.getResolution().contains("同时冻结用户"));

        SysReviewTask banTask = pending(51L, SysReviewTask.TARGET_USER, "101");
        when(reviewTaskMapper.selectOneById(51L)).thenReturn(banTask);
        when(rbacService.hasPermission(2L, "admin:user:ban")).thenReturn(true);
        AdminReviewResolveDTO banDto = new AdminReviewResolveDTO();
        banDto.setResolution("封禁");
        banDto.setUserAction("ban");
        banDto.setContentAction("none");
        service.approve(51L, banDto, 2L);
        verify(adminUserService).ban(eq(101L), any(), eq(2L));
        assertTrue(banTask.getResolution().contains("同时封禁用户"));
    }

    @Test
    @DisplayName("独立下架内容")
    void deleteContent_standalone() {
        SysReviewTask task = pending(80L, SysReviewTask.TARGET_MOMENT, "20");
        when(reviewTaskMapper.selectOneById(80L)).thenReturn(task);

        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("违规下架");
        service.deleteContent(80L, dto, 3L);

        verify(momentsService).adminDeletePost(20L);
        assertEquals(SysReviewTask.STATUS_APPROVED, task.getStatus());
        assertTrue(task.getResolution().contains("已删除动态"));
    }

    @Test
    @DisplayName("驳回时可附带下架内容")
    void reject_with_delete_content() {
        SysReviewTask task = pending(81L, SysReviewTask.TARGET_ANNOUNCEMENT, "21");
        when(reviewTaskMapper.selectOneById(81L)).thenReturn(task);

        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("不通过并删除");
        dto.setContentAction("delete");
        service.reject(81L, dto, 2L);

        verify(groupAnnouncementService).adminDelete(21L);
        assertEquals(SysReviewTask.STATUS_REJECTED, task.getStatus());
        assertTrue(task.getResolution().contains("已删除公告"));
    }

    @Test
    @DisplayName("非法处置动作与已处理任务")
    void invalid_actions_and_already_resolved() {
        SysReviewTask done = pending(60L);
        done.setStatus(SysReviewTask.STATUS_APPROVED);
        when(reviewTaskMapper.selectOneById(60L)).thenReturn(done);
        assertThrows(CustomException.class, () -> service.approve(60L, deleteResolve(), 1L));

        SysReviewTask pending = pending(61L);
        when(reviewTaskMapper.selectOneById(61L)).thenReturn(pending);
        AdminReviewResolveDTO badUser = new AdminReviewResolveDTO();
        badUser.setUserAction("mute");
        assertThrows(CustomException.class, () -> service.approve(61L, badUser, 1L));

        AdminReviewResolveDTO badContent = new AdminReviewResolveDTO();
        badContent.setContentAction("purge");
        assertThrows(CustomException.class, () -> service.approve(61L, badContent, 1L));
    }

    @Test
    @DisplayName("批量驳回")
    void batch_reject() {
        SysReviewTask t1 = pending(70L);
        when(reviewTaskMapper.selectOneById(70L)).thenReturn(t1);

        AdminReviewBatchDTO dto = new AdminReviewBatchDTO();
        dto.setAction("reject");
        dto.setIds(List.of(70L));
        dto.setResolution("不违规");
        var result = service.batch(dto, 1L);
        assertEquals(1, result.getSuccessCount());
        assertEquals(SysReviewTask.STATUS_REJECTED, t1.getStatus());
        verify(notificationService).create(eq(200L), eq(1L), anyString(), isNull(),
                eq("review_rejected"), any(), anyString());
    }

    @Test
    @DisplayName("敏感词创建：多目标类型与去重")
    void createFromSensitiveHit_variants() {
        when(reviewTaskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(reviewTaskMapper.insert(any(SysReviewTask.class))).thenReturn(1);
        when(sysUserMapper.selectOneById(9L)).thenReturn(
                SysUser.builder().id(9L).username("alice").nickname("A").build());

        service.createFromSensitiveHit(9L, SysReviewTask.TARGET_MESSAGE, "88",
                9L, "text", "bad", "alert");
        service.createFromSensitiveHit(9L, SysReviewTask.TARGET_MOMENT, "99",
                null, "post", "spam", "matched");
        service.createFromSensitiveHit(null, "", "x", null, null, null, null);
        service.createFromSensitiveHit(9L, SysReviewTask.TARGET_CONVERSATION, "77",
                77L, "blocked", "w", "blocked");

        SysReviewTask existing = pending(1L);
        when(reviewTaskMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);
        service.createFromSensitiveHit(9L, SysReviewTask.TARGET_MESSAGE, "88",
                9L, "dup", "bad", "alert");
        verify(reviewTaskMapper, times(3)).insert(any(SysReviewTask.class));
    }

    @Test
    @DisplayName("驳回敏感消息时清除提示并关闭关联反馈")
    void reject_clearsHint_and_closesFeedback() {
        SysReviewTask task = pending(80L);
        task.setFeedbackId(8L);
        when(reviewTaskMapper.selectOneById(80L)).thenReturn(task);
        Feedback fb = Feedback.builder().id(8L).status("pending").build();
        when(feedbackMapper.selectOneById(8L)).thenReturn(fb);

        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution("误判");
        service.reject(80L, dto, 3L);

        verify(imPushService).pushToUser(eq(100L), eq("sensitive_alert_clear"), anyMap());
        assertEquals("closed", fb.getStatus());
        assertEquals("误判", fb.getReply());
    }

    @Test
    @DisplayName("群目标通过：解散与处罚群主")
    void approve_group_actions() {
        SysReviewTask groupTask = pending(90L, SysReviewTask.TARGET_GROUP, "300");
        when(reviewTaskMapper.selectOneById(90L)).thenReturn(groupTask);
        ImConversation group = ImConversation.builder().id(300L).type(ImConversation.TYPE_GROUP)
                .ownerId(400L).build();
        when(conversationMapper.selectOneById(300L)).thenReturn(group);

        AdminReviewResolveDTO dissolve = new AdminReviewResolveDTO();
        dissolve.setResolution("解散");
        dissolve.setGroupAction("dissolve");
        service.approve(90L, dissolve, 5L);
        verify(groupService).adminDissolveGroup(300L, 5L);
        assertTrue(groupTask.getResolution().contains("已解散群聊"));

        SysReviewTask groupTask2 = pending(91L, SysReviewTask.TARGET_GROUP, "301");
        when(reviewTaskMapper.selectOneById(91L)).thenReturn(groupTask2);
        when(conversationMapper.selectOneById(301L)).thenReturn(
                ImConversation.builder().id(301L).type(ImConversation.TYPE_GROUP).ownerId(401L).build());
        when(rbacService.hasPermission(5L, "admin:user:freeze")).thenReturn(true);

        AdminReviewResolveDTO freezeOwner = new AdminReviewResolveDTO();
        freezeOwner.setResolution("冻结群主");
        freezeOwner.setGroupAction("freeze_owner");
        service.approve(91L, freezeOwner, 5L);
        verify(adminUserService).freeze(eq(401L), any(), eq(5L));
        assertTrue(groupTask2.getResolution().contains("已冻结群主"));
    }
}
