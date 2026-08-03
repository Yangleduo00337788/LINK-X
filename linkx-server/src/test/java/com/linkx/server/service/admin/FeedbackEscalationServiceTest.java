package com.linkx.server.service.admin;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.admin.impl.FeedbackEscalationServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackEscalationService 超时升级")
class FeedbackEscalationServiceTest {

    @Mock FeedbackMapper feedbackMapper;
    @Mock FeedbackDispatchService feedbackDispatchService;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock AuditLogService auditLogService;

    private LinkxProperties linkxProperties;
    private FeedbackEscalationServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getApp().setFeedbackSlaHours(24);
        linkxProperties.getApp().setFeedbackEscalationEnabled(true);
        linkxProperties.getApp().setFeedbackEscalationAutoReassign(true);
        linkxProperties.getApp().setFeedbackEscalationIntervalHours(24);
        service = new FeedbackEscalationServiceImpl(
                feedbackMapper, feedbackDispatchService, linkxProperties, adminEventPublisher, auditLogService);
    }

    @Test
    @DisplayName("未启用时跳过")
    void disabled_skips() {
        linkxProperties.getApp().setFeedbackEscalationEnabled(false);
        assertEquals(0, service.processOverdueEscalations());
        verifyNoInteractions(feedbackMapper);
    }

    @Test
    @DisplayName("无 assignee 时自动分流并升级")
    void escalate_autoDispatch() {
        Feedback fb = overdueFeedback(1L, null);
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(fb));
        doAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setAssigneeId(99L);
            f.setAssignedAt(new Date());
            return null;
        }).when(feedbackDispatchService).applyAutoDispatch(fb);

        int count = service.processOverdueEscalations();

        assertEquals(1, count);
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).update(captor.capture());
        Feedback updated = captor.getValue();
        assertEquals(1, updated.getEscalationCount());
        assertNotNull(updated.getEscalatedAt());
        verify(adminEventPublisher).publish(eq("feedback_escalated"), eq(1L), anyString());
        verify(auditLogService).logWithExtra(
                eq(SysAuditLog.OperationType.FEEDBACK_ESCALATE),
                contains("#1"),
                isNull(),
                eq("system"),
                isNull(),
                isNull(),
                eq(true),
                isNull(),
                anyString());
    }

    @Test
    @DisplayName("已有 assignee 且开启自动改派时尝试改派")
    void escalate_tryReassign() {
        Feedback fb = overdueFeedback(2L, 10L);
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(fb));
        when(feedbackDispatchService.tryReassign(fb)).thenReturn(true);

        assertEquals(1, service.processOverdueEscalations());

        verify(feedbackDispatchService).tryReassign(fb);
        verify(feedbackDispatchService, never()).applyAutoDispatch(any());
        verify(feedbackMapper).update(fb);
    }

    @Test
    @DisplayName("关闭自动改派时仅记录升级")
    void escalate_noReassign() {
        linkxProperties.getApp().setFeedbackEscalationAutoReassign(false);
        Feedback fb = overdueFeedback(3L, 10L);
        when(feedbackMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(fb));

        assertEquals(1, service.processOverdueEscalations());

        verify(feedbackDispatchService, never()).tryReassign(any());
        verify(feedbackDispatchService, never()).applyAutoDispatch(any());
        assertEquals(1, fb.getEscalationCount());
    }

    private static Feedback overdueFeedback(Long id, Long assigneeId) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -48);
        return Feedback.builder()
                .id(id)
                .status("pending")
                .createTime(cal.getTime())
                .assigneeId(assigneeId)
                .escalationCount(0)
                .build();
    }
}
