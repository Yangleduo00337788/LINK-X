package com.linkx.server.service.customerservice;

import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 客服机器人：FAQ + 反馈/举报进度查询。
 */
@Component
@RequiredArgsConstructor
public class CustomerServiceBotResponder {

    private static final String WELCOME = """
            您好，我是 LinkX客服。
            
            我可以帮您：
            1. 查询反馈/举报进度（发送「查进度」）
            2. 常见问题（发送「帮助」）
            3. 转人工客服（发送「转人工」，即将上线）
            
            正式处理结果会通过「LinkX官方」通知您。""";

    private final SysReviewTaskMapper reviewTaskMapper;

    public String welcomeMessage() {
        return WELCOME;
    }

    public String reply(String userText, List<Feedback> feedbacks) {
        String text = userText == null ? "" : userText.trim();
        if (!StringUtils.hasText(text)) {
            return menuMessage();
        }
        String lower = text.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "帮助", "菜单", "help", "你好", "您好", "hi", "hello")) {
            return menuMessage();
        }
        if (containsAny(lower, "转人工", "人工客服", "真人")) {
            return """
                    人工客服功能即将上线，请先通过以下方式联系我们：
                    · 提交反馈：登录页 / 设置 → 我的反馈记录
                    · 查看官方通知：消息 → LinkX官方
                    
                    您也可以继续描述问题，我会尽力协助。""";
        }
        if (containsAny(lower, "官方", "公告", "linkx官方")) {
            return """
                    系统公告、反馈回复与举报处理结果，请在消息列表底部进入「LinkX官方」查看。
                    
                    发送「查进度」可在此快速查询工单状态。""";
        }
        if (containsAny(lower, "举报", "投诉", "违规")) {
            return buildReportProgress(feedbacks);
        }
        if (containsAny(lower, "进度", "工单", "反馈", "处理", "查询", "状态")) {
            return buildFeedbackProgress(feedbacks);
        }
        return """
                暂未理解您的问题。您可以发送：
                · 「帮助」查看菜单
                · 「查进度」查询反馈/举报
                · 「转人工」了解人工客服（即将上线）""";
    }

    private String menuMessage() {
        return """
                【LinkX客服菜单】
                1. 发送「查进度」— 查询反馈/举报处理状态
                2. 发送「帮助」— 显示本菜单
                3. 发送「转人工」— 人工客服（即将上线）
                
                提示：正式回复与公告请查看「LinkX官方」。""";
    }

    private String buildFeedbackProgress(List<Feedback> feedbacks) {
        Feedback latest = findLatestFeedback(feedbacks, false);
        if (latest == null) {
            return "您暂无反馈记录。可在登录页或「设置 → 我的反馈记录」提交问题。";
        }
        return formatFeedbackCard(latest, "反馈");
    }

    private String buildReportProgress(List<Feedback> feedbacks) {
        Feedback latest = findLatestFeedback(feedbacks, true);
        if (latest == null) {
            return "您暂无举报记录。可在群聊/用户资料中发起举报。";
        }
        String card = formatFeedbackCard(latest, "举报");
        SysReviewTask task = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getFeedbackId).eq(latest.getId())
                        .orderBy(SysReviewTask::getCreateTime, false)
                        .limit(1)
        );
        if (task != null) {
            card += "\n审核状态：" + reviewStatusLabel(task.getStatus());
            if (StringUtils.hasText(task.getResolution())) {
                card += "\n处理说明：" + abbreviate(task.getResolution(), 120);
            }
        }
        card += "\n\n完整说明请查看「LinkX官方」通知。";
        return card;
    }

    private Feedback findLatestFeedback(List<Feedback> feedbacks, boolean reportOnly) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return null;
        }
        for (Feedback item : feedbacks) {
            boolean report = isReportContent(item.getContent());
            if (reportOnly == report) {
                return item;
            }
        }
        return null;
    }

    private String formatFeedbackCard(Feedback feedback, String kind) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(kind).append("进度】\n");
        sb.append("提交时间：").append(formatTime(feedback.getCreateTime())).append('\n');
        sb.append("当前状态：").append(feedbackStatusLabel(feedback.getStatus())).append('\n');
        if (StringUtils.hasText(feedback.getReply())) {
            sb.append("最新回复：").append(abbreviate(feedback.getReply(), 160));
        } else {
            sb.append("说明：我们已收到，请留意「LinkX官方」通知。");
        }
        return sb.toString();
    }

    static boolean isReportContent(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("[举报") || trimmed.startsWith("【举报");
    }

    private static String feedbackStatusLabel(String status) {
        if (status == null) {
            return "待处理";
        }
        return switch (status) {
            case "pending" -> "待处理";
            case "processing" -> "处理中";
            case "replied" -> "已回复";
            case "resolved" -> "已解决";
            case "closed" -> "已关闭";
            default -> status;
        };
    }

    private static String reviewStatusLabel(String status) {
        if (status == null) {
            return "审核中";
        }
        return switch (status) {
            case SysReviewTask.STATUS_PENDING -> "审核中";
            case SysReviewTask.STATUS_APPROVED -> "已处理";
            case SysReviewTask.STATUS_REJECTED -> "未通过";
            default -> status;
        };
    }

    private static String formatTime(Date date) {
        if (date == null) {
            return "—";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String v = text.trim().replaceAll("\\s+", " ");
        if (v.length() <= max) {
            return v;
        }
        return v.substring(0, max) + "…";
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
