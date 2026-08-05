package com.linkx.server.service.admin.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.admin.AdminAudienceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.ReviewEscalationService;
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
public class ReviewEscalationServiceImpl implements ReviewEscalationService {

    private static final int DEFAULT_SLA_HOURS = 24;
    private static final int DEFAULT_INTERVAL_HOURS = 24;
    private static final int BATCH_LIMIT = 200;

    private final SysReviewTaskMapper reviewTaskMapper;
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
                .where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING)
                .and(SysReviewTask::getCreateTime).le(slaCutoff);
        qw.and((QueryWrapper w) -> {
            w.where(SysReviewTask::getEscalatedAt).isNull()
                    .or(SysReviewTask::getEscalatedAt).le(reEscalateBefore);
        });
        qw.limit(0, BATCH_LIMIT);
        List<SysReviewTask> candidates = reviewTaskMapper.selectListByQuery(qw);

        int processed = 0;
        for (SysReviewTask task : candidates) {
            try {
                escalateOne(task);
                processed++;
            } catch (Exception e) {
                log.warn("审核督办失败 id={}: {}", task.getId(), e.getMessage());
            }
        }
        if (processed > 0) {
            log.info("审核超时督办完成，处理 {} 条", processed);
        }
        return processed;
    }

    private void escalateOne(SysReviewTask task) {
        int nextCount = normalizeCount(task.getEscalationCount()) + 1;
        task.setEscalationCount(nextCount);
        task.setEscalatedAt(new Date());
        task.setUpdateTime(new Date());
        reviewTaskMapper.update(task);

        String extraJson = String.format(
                "{\"sourceType\":\"%s\",\"targetType\":\"%s\",\"escalationCount\":%d}",
                task.getSourceType() == null ? "" : task.getSourceType(),
                task.getTargetType() == null ? "" : task.getTargetType(),
                nextCount);
        adminEventPublisher.publishToUsers(
                "review_escalated",
                task.getId(),
                adminAudienceService.reviewOperatorUserIds(),
                extraJson);

        auditLogService.logWithExtra(
                SysAuditLog.OperationType.REVIEW_ESCALATE,
                "审核超时督办 #" + task.getId(),
                null,
                "system",
                null,
                null,
                true,
                null,
                extraJson);
    }

    static boolean isOverdue(SysReviewTask task, LinkxProperties linkxProperties) {
        if (task == null
                || !SysReviewTask.STATUS_PENDING.equals(task.getStatus())
                || task.getCreateTime() == null) {
            return false;
        }
        return !task.getCreateTime().after(slaCutoff(linkxProperties));
    }

    static Date slaCutoff(LinkxProperties linkxProperties) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -resolveSlaHours(linkxProperties));
        return cal.getTime();
    }

    static int resolveSlaHours(LinkxProperties linkxProperties) {
        LinkxProperties.App app = linkxProperties != null ? linkxProperties.getApp() : null;
        Integer hours = app != null ? app.getReviewSlaHours() : null;
        if (hours == null || hours < 1) {
            return DEFAULT_SLA_HOURS;
        }
        return Math.min(hours, 720);
    }

    private boolean isEnabled() {
        LinkxProperties.App app = linkxProperties.getApp();
        return app != null && Boolean.TRUE.equals(app.getReviewEscalationEnabled());
    }

    private Date slaCutoff() {
        return slaCutoff(linkxProperties);
    }

    private Date reEscalateCutoff() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -resolveIntervalHours());
        return cal.getTime();
    }

    private int resolveIntervalHours() {
        LinkxProperties.App app = linkxProperties.getApp();
        Integer hours = app != null ? app.getReviewEscalationIntervalHours() : null;
        if (hours == null || hours < 1) {
            return DEFAULT_INTERVAL_HOURS;
        }
        return Math.min(hours, 720);
    }

    private static int normalizeCount(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }
}
