package com.linkx.server.service.impl;

import com.linkx.server.entity.Feedback;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.FeedbackService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.FeedbackDispatchService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .createTime(new Date())
                .build();
        feedbackMapper.insert(feedback);
        feedbackDispatchService.applyAutoDispatch(feedback);
        adminReviewService.createFromReportFeedback(feedback);

        boolean isReport = content != null && content.trim().startsWith("[举报");
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
}
