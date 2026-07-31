package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.controller.admin.vo.AdminReviewVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminReviewService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    private static final String OFFICIAL_SENDER = "LinkX\u5B98\u65B9";
    private static final Pattern REPORT_PREFIX = Pattern.compile("^\\[举报([^\\]]*)\\]");
    private static final Pattern GROUP_ID_LINE = Pattern.compile("(?m)^群ID:\\s*(.+)$");
    private static final Pattern USER_ID_LINE = Pattern.compile("(?m)^用户ID:\\s*(.+)$");
    private static final Pattern EVIDENCE_KEY_LINE = Pattern.compile(
            "(?m)^\\d+\\.\\s*([\\w./-]+\\.(?:png|jpe?g|gif|webp|bmp))$",
            Pattern.CASE_INSENSITIVE
    );

    private final SysReviewTaskMapper reviewTaskMapper;
    private final FeedbackMapper feedbackMapper;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final AdminEventPublisher adminEventPublisher;
    private final MediaUrlService mediaUrlService;

    @Override
    public PageResultVO<AdminReviewVO> list(AdminReviewQueryDTO query) {
        ensureReportTasks();
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysReviewTask::getTitle).like(kw)
                        .or(SysReviewTask::getContentSnapshot).like(kw)
                        .or(SysReviewTask::getReporterUsername).like(kw)
                        .or(SysReviewTask::getTargetId).like(kw);
            });
        }
        if (StringUtils.hasText(query.getReviewStatus())) {
            qw.and(SysReviewTask::getStatus).eq(query.getReviewStatus().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            qw.and(SysReviewTask::getSourceType).eq(query.getSourceType().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(SysReviewTask::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysReviewTask::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysReviewTask::getCreateTime, false);
        long total = reviewTaskMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminReviewVO> items = reviewTaskMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminReviewVO detail(Long id) {
        return toVO(requireTask(id));
    }

    @Override
    @Transactional
    public void approve(Long id, AdminReviewResolveDTO dto, Long operatorId) {
        resolve(id, SysReviewTask.STATUS_APPROVED, dto, operatorId);
    }

    @Override
    @Transactional
    public void reject(Long id, AdminReviewResolveDTO dto, Long operatorId) {
        resolve(id, SysReviewTask.STATUS_REJECTED, dto, operatorId);
    }

    @Override
    @Transactional
    public void createFromReportFeedback(Feedback feedback) {
        if (feedback == null || feedback.getId() == null || !isReportContent(feedback.getContent())) {
            return;
        }
        SysReviewTask existing = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create().where(SysReviewTask::getFeedbackId).eq(feedback.getId()));
        if (existing != null) {
            return;
        }
        SysReviewTask task = buildFromFeedback(feedback);
        reviewTaskMapper.insert(task);
        publishAdminEvent("review_created", task.getId());
    }

    @Override
    @Transactional
    public void ensureReportTasks() {
        List<Feedback> reports = feedbackMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Feedback::getContent).like("[举报%")
                        .and(Feedback::getStatus).eq("pending")
                        .orderBy(Feedback::getCreateTime, false)
                        .limit(200)
        );
        if (reports.isEmpty()) {
            return;
        }
        Set<Long> feedbackIds = reports.stream().map(Feedback::getId).collect(Collectors.toSet());
        List<SysReviewTask> existing = reviewTaskMapper.selectListByQuery(
                QueryWrapper.create().where(SysReviewTask::getFeedbackId).in(feedbackIds));
        Set<Long> linked = existing.stream()
                .map(SysReviewTask::getFeedbackId)
                .collect(Collectors.toSet());
        for (Feedback feedback : reports) {
            if (!linked.contains(feedback.getId())) {
                SysReviewTask task = buildFromFeedback(feedback);
                reviewTaskMapper.insert(task);
                publishAdminEvent("review_created", task.getId());
            }
        }
    }

    @Override
    public long countPending() {
        ensureReportTasks();
        return reviewTaskMapper.selectCountByQuery(
                QueryWrapper.create().where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING));
    }

    private void resolve(Long id, String status, AdminReviewResolveDTO dto, Long operatorId) {
        SysReviewTask task = requireTask(id);
        if (!SysReviewTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new CustomException(400, "review already resolved");
        }
        Date now = new Date();
        task.setStatus(status);
        task.setResolution(dto != null && StringUtils.hasText(dto.getResolution())
                ? dto.getResolution().trim() : null);
        task.setResolvedBy(operatorId);
        task.setResolvedAt(now);
        task.setUpdateTime(now);
        reviewTaskMapper.update(task);

        if (task.getFeedbackId() != null) {
            Feedback feedback = feedbackMapper.selectOneById(task.getFeedbackId());
            if (feedback != null && !"closed".equals(feedback.getStatus())) {
                feedback.setStatus("closed");
                if (StringUtils.hasText(task.getResolution())) {
                    feedback.setReply(task.getResolution());
                    feedback.setReplyTime(now);
                }
                feedbackMapper.update(feedback);
            }
        }

        notifyReporter(task, status, operatorId);
        publishAdminEvent("review_resolved", task.getId());
    }

    private void notifyReporter(SysReviewTask task, String status, Long operatorId) {
        Long reporterId = task.getReporterUserId();
        if (reporterId == null) {
            return;
        }
        boolean approved = SysReviewTask.STATUS_APPROVED.equals(status);
        String type = approved ? "review_approved" : "review_rejected";
        String title = approved ? "\u4E3E\u62A5\u5DF2\u5904\u7406" : "\u4E3E\u62A5\u672A\u901A\u8FC7";
        StringBuilder content = new StringBuilder();
        content.append("\u3010").append(title).append("\u3011\n");
        content.append("\u6807\u9898\uFF1A").append(abbreviate(task.getTitle(), 40)).append('\n');
        if (StringUtils.hasText(task.getResolution())) {
            content.append("\u5904\u7406\u610F\u89C1\uFF1A").append(abbreviate(task.getResolution(), 200));
        } else {
            content.append("\u8BE6\u60C5\uFF1A")
                    .append(approved
                            ? "\u6211\u4EEC\u5DF2\u6838\u5B9E\u5E76\u5B8C\u6210\u5904\u7406\uFF0C\u611F\u8C22\u4F60\u7684\u53CD\u9988"
                            : "\u7ECF\u6838\u5B9E\u6682\u672A\u8BA4\u5B9A\u8FDD\u89C4\uFF0C\u5982\u6709\u66F4\u591A\u8BC1\u636E\u53EF\u518D\u6B21\u4E3E\u62A5");
        }
        String body = content.toString();
        Long relatedId = task.getFeedbackId() != null ? task.getFeedbackId() : task.getId();
        notificationService.create(
                reporterId,
                operatorId,
                OFFICIAL_SENDER,
                null,
                type,
                relatedId,
                body
        );
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("relatedId", String.valueOf(relatedId));
        payload.put("content", body);
        imPushService.pushToUser(reporterId, "notification_refresh", payload);
    }

    private void publishAdminEvent(String type, Long reviewId) {
        adminEventPublisher.publish(type, reviewId);
    }

    private SysReviewTask buildFromFeedback(Feedback feedback) {
        String content = feedback.getContent() == null ? "" : feedback.getContent();
        Matcher prefix = REPORT_PREFIX.matcher(content);
        String reportKind = prefix.find() ? prefix.group(1) : "内容";
        String targetType = reportKind.contains("群") ? "group" : "user";
        String targetId = null;
        Matcher gid = GROUP_ID_LINE.matcher(content);
        if (gid.find()) {
            targetId = gid.group(1).trim();
        } else {
            Matcher uid = USER_ID_LINE.matcher(content);
            if (uid.find()) {
                targetId = uid.group(1).trim();
            }
        }
        String title = "举报" + reportKind;
        if (content.length() > 40) {
            title = content.lines().findFirst().orElse(title);
            if (title.length() > 64) {
                title = title.substring(0, 64);
            }
        }
        Date now = new Date();
        return SysReviewTask.builder()
                .sourceType(SysReviewTask.SOURCE_REPORT)
                .targetType(targetType)
                .targetId(targetId)
                .reporterUserId(feedback.getUserId())
                .reporterUsername(feedback.getUsername())
                .title(title)
                .contentSnapshot(content)
                .riskLevel("medium")
                .status(SysReviewTask.STATUS_PENDING)
                .feedbackId(feedback.getId())
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private static boolean isReportContent(String content) {
        return StringUtils.hasText(content) && content.trim().startsWith("[举报");
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "\u2026";
    }

    private AdminReviewVO toVO(SysReviewTask task) {
        return AdminReviewVO.builder()
                .id(task.getId())
                .sourceType(task.getSourceType())
                .targetType(task.getTargetType())
                .targetId(task.getTargetId())
                .reporterUserId(task.getReporterUserId())
                .reporterUsername(task.getReporterUsername())
                .title(task.getTitle())
                .contentSnapshot(task.getContentSnapshot())
                .evidenceUrls(resolveEvidenceUrls(task.getContentSnapshot()))
                .riskLevel(task.getRiskLevel())
                .status(task.getStatus())
                .feedbackId(task.getFeedbackId())
                .resolution(task.getResolution())
                .resolvedBy(task.getResolvedBy())
                .resolvedAt(task.getResolvedAt())
                .createTime(task.getCreateTime())
                .build();
    }

    /** 从举报正文解析证据 object key 并签发可访问 URL */
    private List<String> resolveEvidenceUrls(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Matcher m = EVIDENCE_KEY_LINE.matcher(content);
        while (m.find()) {
            String key = m.group(1).trim();
            if (key.contains("..") || key.startsWith("/") || key.contains("://")) {
                continue;
            }
            try {
                String url = mediaUrlService.resolveFile(key);
                if (StringUtils.hasText(url)) {
                    urls.add(url);
                }
            } catch (Exception ignored) {
                // 单张失败不影响其它证据
            }
        }
        return urls;
    }

    private SysReviewTask requireTask(Long id) {
        SysReviewTask task = reviewTaskMapper.selectOneById(id);
        if (task == null) {
            throw new CustomException(404, "review not found");
        }
        return task;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
