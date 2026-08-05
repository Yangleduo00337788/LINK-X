package com.linkx.server.service.admin.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.admin.AdminAudienceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.FeedbackDispatchService;
import com.linkx.server.service.admin.FeedbackEscalationService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackEscalationServiceImpl implements FeedbackEscalationService {

    private static final int DEFAULT_SLA_HOURS = 24;
    private static final int DEFAULT_INTERVAL_HOURS = 24;
    private static final int BATCH_LIMIT = 200;

    private final FeedbackMapper feedbackMapper;
    private final FeedbackDispatchService feedbackDispatchService;
    private final LinkxProperties linkxProperties;
    private final AdminEventPublisher adminEventPublisher;
    private final AdminAudienceService adminAudienceService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public int processOverdueEscalations() {
        if (!isEnabled()) {
            return 0;
        }
        Date slaCutoff = slaCutoff();
        Date reEscalateBefore = reEscalateCutoff();
        QueryWrapper qw = QueryWrapper.create()
                .where(Feedback::getStatus).eq("pending")
                .and(Feedback::getCreateTime).le(slaCutoff);
        qw.and((QueryWrapper w) -> {
            w.where(Feedback::getEscalatedAt).isNull()
                    .or(Feedback::getEscalatedAt).le(reEscalateBefore);
        });
        qw.limit(0, BATCH_LIMIT);
        List<Feedback> candidates = feedbackMapper.selectListByQuery(qw);

        int processed = 0;
        for (Feedback feedback : candidates) {
            try {
                escalateOne(feedback);
                processed++;
            } catch (Exception e) {
                log.warn("反馈升级失败 id={}: {}", feedback.getId(), e.getMessage());
            }
        }
        if (processed > 0) {
            log.info("反馈超时升级完成，处理 {} 条", processed);
        }
        return processed;
    }

    private void escalateOne(Feedback feedback) {
        Long previousAssignee = feedback.getAssigneeId();
        boolean reassigned = false;

        if (feedback.getAssigneeId() == null) {
            feedbackDispatchService.applyAutoDispatch(feedback);
            reassigned = feedback.getAssigneeId() != null;
        } else if (isAutoReassign()) {
            reassigned = feedbackDispatchService.tryReassign(feedback);
        }

        int nextCount = normalizeCount(feedback.getEscalationCount()) + 1;
        feedback.setEscalationCount(nextCount);
        feedback.setEscalatedAt(new Date());
        feedbackMapper.update(feedback);

        String extraJson = String.format(
                "{\"previousAssigneeId\":\"%s\",\"assigneeId\":\"%s\",\"escalationCount\":%d,\"reassigned\":%s}",
                previousAssignee != null ? previousAssignee : "",
                feedback.getAssigneeId() != null ? feedback.getAssigneeId() : "",
                nextCount,
                reassigned);
        adminEventPublisher.publishToUsers(
                "feedback_escalated",
                feedback.getId(),
                adminAudienceService.feedbackOperatorUserIds(),
                extraJson);

        auditLogService.logWithExtra(
                SysAuditLog.OperationType.FEEDBACK_ESCALATE,
                "反馈超时升级 #" + feedback.getId(),
                null,
                "system",
                null,
                null,
                true,
                null,
                extraJson);
    }

    private boolean isEnabled() {
        LinkxProperties.App app = linkxProperties.getApp();
        return app != null && Boolean.TRUE.equals(app.getFeedbackEscalationEnabled());
    }

    private boolean isAutoReassign() {
        LinkxProperties.App app = linkxProperties.getApp();
        return app == null || !Boolean.FALSE.equals(app.getFeedbackEscalationAutoReassign());
    }

    private Date slaCutoff() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -resolveSlaHours());
        return cal.getTime();
    }

    private Date reEscalateCutoff() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -resolveIntervalHours());
        return cal.getTime();
    }

    private int resolveSlaHours() {
        LinkxProperties.App app = linkxProperties.getApp();
        Integer hours = app != null ? app.getFeedbackSlaHours() : null;
        if (hours == null || hours < 1) {
            return DEFAULT_SLA_HOURS;
        }
        return Math.min(hours, 720);
    }

    private int resolveIntervalHours() {
        LinkxProperties.App app = linkxProperties.getApp();
        Integer hours = app != null ? app.getFeedbackEscalationIntervalHours() : null;
        if (hours == null || hours < 1) {
            return DEFAULT_INTERVAL_HOURS;
        }
        return Math.min(hours, 720);
    }

    private static int normalizeCount(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }
}
