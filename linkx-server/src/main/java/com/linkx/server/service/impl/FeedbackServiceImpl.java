package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.FeedbackReplyVO;
import com.linkx.server.controller.vo.FeedbackVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.FeedbackReplyService;
import com.linkx.server.service.FeedbackService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.FeedbackDispatchService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    private static final String OFFICIAL_SENDER = "LinkX\u5B98\u65B9";

    private final FeedbackMapper feedbackMapper;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final AdminReviewService adminReviewService;
    private final FeedbackDispatchService feedbackDispatchService;
    private final FeedbackReplyService feedbackReplyService;
    private final AdminEventPublisher adminEventPublisher;

    @Override
    @Transactional
    public Feedback create(Long userId, String username, String type, String content, String contact) {
        Feedback feedback = Feedback.builder()
                .userId(userId)
                .username(username)
                .type(type)
                .content(content)
                .contact(contact)
                .status("pending")
                .escalationCount(0)
                .createTime(new Date())
                .build();
        feedbackMapper.insert(feedback);
        feedbackDispatchService.applyAutoDispatch(feedback);
        adminReviewService.createFromReportFeedback(feedback);

        boolean isReport = isReportContent(content);
        publishAdminFeedbackCreated(feedback, isReport);

        String notifyContent = isReport
                ? "\u3010\u4E3E\u62A5\u5DF2\u63D0\u4EA4\u3011\n"
                    + "\u7C7B\u578B\uFF1A\u7528\u6237\u4E3E\u62A5\n"
                    + "\u4F60\u7684\u4E3E\u62A5\uFF1A" + abbreviate(content, 200)
                    + "\n\u8BE6\u60C5\uFF1A\u6211\u4EEC\u5DF2\u6536\u5230\uFF0C\u5C06\u5C3D\u5FEB\u6838\u5B9E\u5904\u7406"
                : "\u3010\u53CD\u9988\u5DF2\u63D0\u4EA4\u3011\n"
                    + "\u7C7B\u578B\uFF1A" + typeLabel(type) + "\n"
                    + "\u4F60\u7684\u53CD\u9988\uFF1A" + abbreviate(content, 200)
                    + "\n\u8BE6\u60C5\uFF1A\u6211\u4EEC\u5DF2\u6536\u5230\uFF0C\u4F1A\u5C3D\u5FEB\u5904\u7406";
        notificationService.create(
                userId,
                null,
                OFFICIAL_SENDER,
                null,
                "feedback_submitted",
                feedback.getId(),
                notifyContent
        );
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "feedback_submitted");
        payload.put("relatedId", String.valueOf(feedback.getId()));
        payload.put("content", notifyContent);
        imPushService.pushToUser(userId, "notification_refresh", payload);
        return feedback;
    }

    @Override
    public List<Feedback> listByUser(Long userId) {
        return feedbackMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Feedback::getUserId).eq(userId)
                        .orderBy(Feedback::getCreateTime, false)
        );
    }

    @Override
    public FeedbackVO getDetail(Long userId, Long feedbackId) {
        Feedback feedback = requireOwned(userId, feedbackId);
        return toVO(feedback, true);
    }

    @Override
    public List<FeedbackReplyVO> listReplies(Long userId, Long feedbackId) {
        requireOwned(userId, feedbackId);
        return feedbackReplyService.listByFeedbackId(feedbackId);
    }

    @Override
    @Transactional
    public FeedbackReplyVO userReply(Long userId, String username, Long feedbackId, String content) {
        Feedback feedback = requireOwned(userId, feedbackId);
        if ("closed".equals(feedback.getStatus())) {
            throw new CustomException(400, "feedback is closed");
        }
        FeedbackReplyVO reply = feedbackReplyService.addUserReply(feedback, userId, username, content);
        feedback.setStatus("pending");
        feedbackMapper.update(feedback);
        return reply;
    }

    private Feedback requireOwned(Long userId, Long feedbackId) {
        Feedback feedback = feedbackMapper.selectOneById(feedbackId);
        if (feedback == null || !userId.equals(feedback.getUserId())) {
            throw new CustomException(404, "feedback not found");
        }
        return feedback;
    }

    private FeedbackVO toVO(Feedback feedback, boolean withReplies) {
        FeedbackVO.FeedbackVOBuilder builder = FeedbackVO.builder()
                .id(feedback.getId())
                .type(feedback.getType())
                .content(feedback.getContent())
                .status(feedback.getStatus())
                .reply(feedback.getReply())
                .replyTime(feedback.getReplyTime())
                .createTime(feedback.getCreateTime());
        if (withReplies) {
            builder.replies(feedbackReplyService.listByFeedbackId(feedback.getId()));
        }
        return builder.build();
    }

    private static String typeLabel(String type) {
        if (type == null) return "\u5176\u4ED6";
        return switch (type) {
            case "bug" -> "\u7F3A\u9677\u53CD\u9988";
            case "suggestion" -> "\u529F\u80FD\u5EFA\u8BAE";
            default -> "\u5176\u4ED6";
        };
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "\u2026";
    }

    private static boolean isReportContent(String content) {
        return content != null && content.trim().startsWith("[举报");
    }

    /** 普通反馈通知管理端；举报类由 review_created 事件覆盖，避免重复提醒。 */
    private void publishAdminFeedbackCreated(Feedback feedback, boolean isReport) {
        if (isReport || feedback == null || feedback.getId() == null) {
            return;
        }
        Long feedbackId = feedback.getId();
        String extraJson = String.format(
                "{\"feedbackType\":\"%s\",\"username\":\"%s\",\"content\":\"%s\"}",
                escapeJson(feedback.getType() == null ? "" : feedback.getType()),
                escapeJson(feedback.getUsername() == null ? "" : feedback.getUsername()),
                escapeJson(abbreviate(feedback.getContent(), 200)));
        Runnable publish = () -> adminEventPublisher.publish("feedback_created", feedbackId, extraJson);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
