package com.linkx.server.service.admin;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.admin.impl.ReviewEscalationServiceImpl;
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
@DisplayName("ReviewEscalationService 审核督办")
class ReviewEscalationServiceTest {

    @Mock SysReviewTaskMapper reviewTaskMapper;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock AuditLogService auditLogService;

    private LinkxProperties linkxProperties;
    private ReviewEscalationServiceImpl service;

    @BeforeEach
    void setUp() {
        linkxProperties = new LinkxProperties();
        linkxProperties.getApp().setReviewSlaHours(24);
        linkxProperties.getApp().setReviewEscalationEnabled(true);
        linkxProperties.getApp().setReviewEscalationIntervalHours(24);
        service = new ReviewEscalationServiceImpl(
                reviewTaskMapper, linkxProperties, adminEventPublisher, auditLogService);
    }

    @Test
    @DisplayName("未启用时跳过")
    void disabled_skips() {
        linkxProperties.getApp().setReviewEscalationEnabled(false);
        assertEquals(0, service.processOverdueEscalations());
        verifyNoInteractions(reviewTaskMapper);
    }

    @Test
    @DisplayName("超时待审任务记录督办并推送")
    void escalate_pendingReview() {
        SysReviewTask task = overdueTask(1L);
        when(reviewTaskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(task));

        assertEquals(1, service.processOverdueEscalations());

        ArgumentCaptor<SysReviewTask> captor = ArgumentCaptor.forClass(SysReviewTask.class);
        verify(reviewTaskMapper).update(captor.capture());
        assertEquals(1, captor.getValue().getEscalationCount());
        assertNotNull(captor.getValue().getEscalatedAt());
        verify(adminEventPublisher).publish(eq("review_escalated"), eq(1L), anyString());
        verify(auditLogService).logWithExtra(
                eq(SysAuditLog.OperationType.REVIEW_ESCALATE),
                contains("#1"),
                isNull(),
                eq("system"),
                isNull(),
                isNull(),
                eq(true),
                isNull(),
                anyString());
    }

    private static SysReviewTask overdueTask(Long id) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -48);
        return SysReviewTask.builder()
                .id(id)
                .sourceType(SysReviewTask.SOURCE_SENSITIVE)
                .targetType(SysReviewTask.TARGET_MESSAGE)
                .status(SysReviewTask.STATUS_PENDING)
                .createTime(cal.getTime())
                .escalationCount(0)
                .build();
    }
}
