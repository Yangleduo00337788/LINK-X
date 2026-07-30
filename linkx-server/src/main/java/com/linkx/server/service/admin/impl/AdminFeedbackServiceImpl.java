package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackReplyDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;
import com.linkx.server.entity.Feedback;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminFeedbackService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    private static final String OFFICIAL_SENDER = "LinkX\u5B98\u65B9";

    private final FeedbackMapper feedbackMapper;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;

    @Override
    public PageResultVO<AdminFeedbackVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(Feedback::getUsername).like(kw)
                        .or(Feedback::getContent).like(kw)
                        .or(Feedback::getType).like(kw);
            });
        }
        if (query.getStartTime() != null) {
            qw.and(Feedback::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(Feedback::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(Feedback::getCreateTime, false);
        long total = feedbackMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminFeedbackVO> items = feedbackMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminFeedbackVO detail(Long id) {
        return toVO(requireFeedback(id));
    }

    @Override
    @Transactional
    public void reply(Long id, AdminFeedbackReplyDTO dto, Long operatorId) {
        Feedback feedback = requireFeedback(id);
        Date now = new Date();
        String replyText = dto.getContent().trim();
        feedback.setReply(replyText);
        feedback.setReplyTime(now);
        feedback.setStatus("replied");
        feedbackMapper.update(feedback);

        String content = buildDetailedContent(
                "\u5B98\u65B9\u56DE\u590D",
                feedback.getType(),
                feedback.getContent(),
                replyText
        );
        notificationService.create(
                feedback.getUserId(),
                operatorId,
                OFFICIAL_SENDER,
                null,
                "feedback_replied",
                feedback.getId(),
                content
        );
        pushFeedback(feedback.getUserId(), "feedback_replied", feedback.getId(), content);
    }

    @Override
    @Transactional
    public void close(Long id, Long operatorId) {
        Feedback feedback = requireFeedback(id);
        feedback.setStatus("closed");
        feedbackMapper.update(feedback);

        String content = buildDetailedContent(
                "\u53CD\u9988\u5DF2\u5173\u95ED",
                feedback.getType(),
                feedback.getContent(),
                feedback.getReply()
        );
        notificationService.create(
                feedback.getUserId(),
                operatorId,
                OFFICIAL_SENDER,
                null,
                "feedback_closed",
                feedback.getId(),
                content
        );
        pushFeedback(feedback.getUserId(), "feedback_closed", feedback.getId(), content);
    }

    @Override
    @Transactional
    public void reopen(Long id, Long operatorId) {
        Feedback feedback = requireFeedback(id);
        feedback.setStatus("pending");
        feedbackMapper.update(feedback);

        String content = buildDetailedContent(
                "\u53CD\u9988\u5DF2\u91CD\u65B0\u6253\u5F00",
                feedback.getType(),
                feedback.getContent(),
                "\u6211\u4EEC\u4F1A\u7EE7\u7EED\u5904\u7406\u4F60\u7684\u53CD\u9988"
        );
        notificationService.create(
                feedback.getUserId(),
                operatorId,
                OFFICIAL_SENDER,
                null,
                "feedback_reopened",
                feedback.getId(),
                content
        );
        pushFeedback(feedback.getUserId(), "feedback_reopened", feedback.getId(), content);
    }

    private void pushFeedback(Long userId, String type, Long relatedId, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("relatedId", String.valueOf(relatedId));
        payload.put("content", content);
        imPushService.pushToUser(userId, "notification_refresh", payload);
    }

    private static String buildDetailedContent(String title, String type, String feedbackContent, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u3010").append(title).append("\u3011\n");
        sb.append("\u7C7B\u578B\uFF1A").append(typeLabel(type)).append('\n');
        sb.append("\u4F60\u7684\u53CD\u9988\uFF1A").append(abbreviate(feedbackContent, 120));
        if (StringUtils.hasText(extra)) {
            sb.append('\n').append("\u8BE6\u60C5\uFF1A").append(abbreviate(extra, 200));
        }
        return sb.toString();
    }

    private static String typeLabel(String type) {
        if (type == null) return "\u5176\u4ED6";
        return switch (type) {
            case "bug" -> "\u7F3A\u9677\u53CD\u9988";
            case "suggestion" -> "\u529F\u80FD\u5EFA\u8BAE";
            default -> "\u5176\u4ED6";
        };
    }

    private AdminFeedbackVO toVO(Feedback feedback) {
        String reply = feedback.getReply();
        if (!StringUtils.hasText(reply)) {
            String contact = feedback.getContact();
            if (contact != null && contact.contains("[admin_reply] ")) {
                int idx = contact.lastIndexOf("[admin_reply] ");
                reply = contact.substring(idx + "[admin_reply] ".length()).trim();
            }
        }
        return AdminFeedbackVO.builder()
                .id(feedback.getId())
                .userId(feedback.getUserId())
                .username(feedback.getUsername())
                .type(feedback.getType())
                .content(feedback.getContent())
                .contact(feedback.getContact())
                .status(feedback.getStatus())
                .reply(reply)
                .createTime(feedback.getCreateTime())
                .build();
    }

    private Feedback requireFeedback(Long id) {
        Feedback feedback = feedbackMapper.selectOneById(id);
        if (feedback == null) {
            throw new CustomException(404, "feedback not found");
        }
        return feedback;
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

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "\u2026";
    }
}
