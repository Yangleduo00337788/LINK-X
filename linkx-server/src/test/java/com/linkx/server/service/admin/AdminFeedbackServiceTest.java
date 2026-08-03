package com.linkx.server.service.admin;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminFeedbackAssignDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackQueryDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackReplyDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.impl.AdminFeedbackServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeedbackService 反馈管理")
class AdminFeedbackServiceTest {

    @Mock FeedbackMapper feedbackMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MessageNotificationService notificationService;
    @Mock ImMessagePushService imPushService;
    @Mock com.linkx.server.service.FeedbackReplyService feedbackReplyService;

    private LinkxProperties linkxProperties;
    private AdminFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        service = new AdminFeedbackServiceImpl(
                feedbackMapper, sysUserMapper, notificationService, imPushService, linkxProperties,
                feedbackReplyService);
    }

    private Feedback feedback(Long id, String status, String type) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -48);
        return Feedback.builder()
                .id(id)
                .userId(100L)
                .username("alice")
                .type(type)
                .content("Something is wrong with the app")
                .contact("alice@test.com")
                .status(status)
                .createTime(cal.getTime())
                .build();
    }

    @Test
    @DisplayName("列表分页与筛选")
    void list_ok() {
        when(feedbackMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(feedback(1L, "pending", "bug")));

        AdminFeedbackQueryDTO q = new AdminFeedbackQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("alice");
        q.setFeedbackStatus("pending");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());
        q.setOverdueOnly(true);

        var page = service.list(q);
        assertEquals(1, page.getTotal());
        AdminFeedbackVO vo = page.getItems().get(0);
        assertEquals("alice", vo.getUsername());
        assertEquals("bug", vo.getType());
        assertTrue(Boolean.TRUE.equals(vo.getOverdue()));
    }

    @Test
    @DisplayName("导出与逾期计数")
    void export_and_countOverdue() {
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(feedback(2L, "pending", "suggestion")));
        when(feedbackMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);

        List<AdminFeedbackVO> exported = service.listForExport(new AdminFeedbackQueryDTO());
        assertEquals(1, exported.size());
        assertEquals("suggestion", exported.get(0).getType());
        assertEquals(3L, service.countOverdue());
    }

    @Test
    @DisplayName("详情与 contact 内嵌回复")
    void detail_and_contactReply() {
        Feedback fb = feedback(3L, "pending", "other");
        fb.setContact("email@test.com [admin_reply] legacy reply text");
        when(feedbackMapper.selectOneById(3L)).thenReturn(fb);

        AdminFeedbackVO vo = service.detail(3L);
        assertEquals("legacy reply text", vo.getReply());

        when(feedbackMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(99L));
    }

    @Test
    @DisplayName("回复/关闭/重开")
    void reply_close_reopen() {
        Feedback fb = feedback(4L, "pending", "bug");
        fb.setReply("old");
        when(feedbackMapper.selectOneById(4L)).thenReturn(fb);

        AdminFeedbackReplyDTO replyDto = new AdminFeedbackReplyDTO();
        replyDto.setContent("  fixed in v2 ");
        service.reply(4L, replyDto, 9L);
        assertEquals("replied", fb.getStatus());
        assertEquals("fixed in v2", fb.getReply());
        verify(feedbackReplyService).addAdminReply(eq(fb), eq("fixed in v2"), eq(9L));
        verify(notificationService).create(eq(100L), eq(9L), anyString(), isNull(),
                eq("feedback_replied"), eq(4L), anyString());
        verify(imPushService).pushToUser(eq(100L), eq("notification_refresh"), anyMap());

        service.close(4L, 9L);
        assertEquals("closed", fb.getStatus());
        verify(notificationService).create(eq(100L), eq(9L), anyString(), isNull(),
                eq("feedback_closed"), eq(4L), anyString());

        service.reopen(4L, 9L);
        assertEquals("pending", fb.getStatus());
        verify(notificationService).create(eq(100L), eq(9L), anyString(), isNull(),
                eq("feedback_reopened"), eq(4L), anyString());
    }

    @Test
    @DisplayName("长文本 abbreviate 与 SLA 配置")
    void abbreviate_and_sla() {
        linkxProperties.getApp().setFeedbackSlaHours(48);
        Feedback fb = feedback(5L, "pending", null);
        fb.setCreateTime(new Date());
        String longContent = "word ".repeat(50);
        fb.setContent(longContent);
        when(feedbackMapper.selectOneById(5L)).thenReturn(fb);

        AdminFeedbackVO vo = service.detail(5L);
        assertEquals(longContent, vo.getContent());
        assertFalse(Boolean.TRUE.equals(vo.getOverdue()));

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -72);
        fb.setCreateTime(cal.getTime());
        vo = service.detail(5L);
        assertTrue(Boolean.TRUE.equals(vo.getOverdue()));

        linkxProperties.getApp().setFeedbackSlaHours(800);
        vo = service.detail(5L);
        assertFalse(Boolean.TRUE.equals(vo.getOverdue()));
    }

    @Test
    @DisplayName("分页默认值")
    void list_defaultPagination() {
        when(feedbackMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AdminFeedbackQueryDTO q = new AdminFeedbackQueryDTO();
        var page = service.list(q);
        assertEquals(0, page.getTotal());
        assertEquals(1, page.getPage());
    }

    @Test
    @DisplayName("推送 payload 结构")
    void push_payload() {
        Feedback fb = feedback(6L, "pending", "bug");
        when(feedbackMapper.selectOneById(6L)).thenReturn(fb);

        AdminFeedbackReplyDTO replyDto = new AdminFeedbackReplyDTO();
        replyDto.setContent("thanks");
        service.reply(6L, replyDto, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(imPushService).pushToUser(eq(100L), eq("notification_refresh"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("feedback_replied", payload.get("type"));
        assertEquals("6", payload.get("relatedId"));
        assertNotNull(payload.get("content"));
    }

    @Test
    @DisplayName("指派与取消指派")
    void assign_and_unassign() {
        Feedback fb = feedback(7L, "pending", "bug");
        when(feedbackMapper.selectOneById(7L)).thenReturn(fb);
        when(sysUserMapper.selectOneById(8L)).thenReturn(SysUser.builder().id(8L).username("handler").build());

        AdminFeedbackAssignDTO dto = new AdminFeedbackAssignDTO();
        dto.setAssigneeId(8L);
        service.assign(7L, dto, 1L);
        assertEquals(8L, fb.getAssigneeId());
        assertNotNull(fb.getAssignedAt());

        dto.setAssigneeId(null);
        service.assign(7L, dto, 1L);
        assertNull(fb.getAssigneeId());
        assertNull(fb.getAssignedAt());
    }
}
