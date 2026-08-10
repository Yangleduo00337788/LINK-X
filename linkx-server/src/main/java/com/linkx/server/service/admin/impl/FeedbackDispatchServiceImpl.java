package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.AdminAudienceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.FeedbackDispatchService;
import com.linkx.server.service.admin.rule.FeedbackAssigneeResolver;
import com.linkx.server.service.admin.rule.FeedbackDispatchResult;
import com.linkx.server.service.admin.rule.FeedbackRuleConditionEvaluator;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedbackDispatchServiceImpl implements FeedbackDispatchService {

    private final SysFeedbackDispatchRuleMapper ruleMapper;
    private final FeedbackMapper feedbackMapper;
    private final FeedbackRuleConditionEvaluator conditionEvaluator;
    private final FeedbackAssigneeResolver assigneeResolver;
    private final AdminEventPublisher adminEventPublisher;
    private final AdminAudienceService adminAudienceService;

    @Override
    public Optional<Long> matchAssignee(Feedback feedback) {
        return evaluate(feedback).map(FeedbackDispatchResult::getAssigneeId);
    }

    @Override
    public Optional<FeedbackDispatchResult> evaluate(Feedback feedback) {
        if (feedback == null) {
            return Optional.empty();
        }
        List<SysFeedbackDispatchRule> rules = loadEnabledRules();
        for (SysFeedbackDispatchRule rule : rules) {
            if (!conditionEvaluator.matches(rule, feedback)) {
                continue;
            }
            FeedbackDispatchResult result = buildResult(rule, feedback);
            if (shouldAssign(result)) {
                return Optional.of(result);
            }
            if (shouldNotifyOnly(result)) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void applyAutoDispatch(Feedback feedback) {
        if (feedback == null || feedback.getId() == null || feedback.getAssigneeId() != null) {
            return;
        }
        evaluate(feedback).ifPresent(result -> execute(result, feedback));
    }

    @Override
    @Transactional
    public boolean tryReassign(Feedback feedback) {
        if (feedback == null || feedback.getId() == null) {
            return false;
        }
        return evaluate(feedback)
                .filter(FeedbackDispatchServiceImpl::shouldAssign)
                .filter(result -> result.getAssigneeId() != null)
                .filter(result -> feedback.getAssigneeId() == null
                        || !result.getAssigneeId().equals(feedback.getAssigneeId()))
                .map(result -> {
                    execute(result, feedback);
                    return true;
                })
                .orElse(false);
    }

    private void execute(FeedbackDispatchResult result, Feedback feedback) {
        boolean assigned = false;
        if (shouldAssign(result) && result.getAssigneeId() != null) {
            feedback.setAssigneeId(result.getAssigneeId());
            feedback.setAssignedAt(new Date());
            feedbackMapper.update(feedback);
            assigned = true;
            publishAssigned(result, feedback);
        }
        if (shouldNotify(result)) {
            publishNotify(result, feedback, assigned);
        }
    }

    private FeedbackDispatchResult buildResult(SysFeedbackDispatchRule rule, Feedback feedback) {
        String actionType = normalizeActionType(rule.getActionType());
        Long assigneeId = null;
        if (shouldAssignAction(actionType)) {
            assigneeId = assigneeResolver.resolve(rule).orElse(null);
        }
        return FeedbackDispatchResult.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .assigneeId(assigneeId)
                .actionType(actionType)
                .assigneeSource(rule.getAssigneeSource())
                .assigned(false)
                .notified(false)
                .notifyRoles(rule.getNotifyRoles())
                .notifyChannels(rule.getNotifyChannels())
                .build();
    }

    private void publishAssigned(FeedbackDispatchResult result, Feedback feedback) {
        String extraJson = String.format(
                Locale.ROOT,
                "{\"ruleId\":\"%s\",\"ruleName\":\"%s\",\"assigneeId\":\"%s\",\"actionType\":\"%s\"}",
                result.getRuleId() != null ? result.getRuleId() : "",
                escapeJson(result.getRuleName()),
                result.getAssigneeId() != null ? result.getAssigneeId() : "",
                result.getActionType() != null ? result.getActionType() : "");
        adminEventPublisher.publishToUsers(
                "feedback_assigned", feedback.getId(), adminAudienceService.feedbackOperatorUserIds(), extraJson);
    }

    private void publishNotify(FeedbackDispatchResult result, Feedback feedback, boolean assigned) {
        String extraJson = String.format(
                Locale.ROOT,
                "{\"ruleId\":\"%s\",\"ruleName\":\"%s\",\"assigneeId\":\"%s\",\"notifyRoles\":\"%s\",\"notifyChannels\":\"%s\",\"assigned\":%s}",
                result.getRuleId() != null ? result.getRuleId() : "",
                escapeJson(result.getRuleName()),
                result.getAssigneeId() != null ? result.getAssigneeId() : "",
                escapeJson(result.getNotifyRoles()),
                escapeJson(result.getNotifyChannels()),
                assigned);
        adminEventPublisher.publishToUsers(
                "feedback_notify", feedback.getId(), adminAudienceService.feedbackOperatorUserIds(), extraJson);
    }

    private List<SysFeedbackDispatchRule> loadEnabledRules() {
        return ruleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysFeedbackDispatchRule::getDeleted).eq(0)
                        .and(SysFeedbackDispatchRule::getEnabled).eq(true)
                        .orderBy(SysFeedbackDispatchRule::getPriority, false)
                        .orderBy(SysFeedbackDispatchRule::getUpdateTime, false));
    }

    private static boolean shouldAssign(FeedbackDispatchResult result) {
        return shouldAssignAction(result.getActionType()) && result.getAssigneeId() != null;
    }

    private static boolean shouldNotifyOnly(FeedbackDispatchResult result) {
        return "notify".equalsIgnoreCase(result.getActionType())
                && StringUtils.hasText(result.getNotifyRoles());
    }

    private static boolean shouldNotify(FeedbackDispatchResult result) {
        String actionType = normalizeActionType(result.getActionType());
        if (!StringUtils.hasText(result.getNotifyRoles())) {
            return false;
        }
        return "notify".equalsIgnoreCase(actionType) || "assign_notify".equalsIgnoreCase(actionType);
    }

    private static boolean shouldAssignAction(String actionType) {
        String normalized = normalizeActionType(actionType);
        return "assign".equalsIgnoreCase(normalized) || "assign_notify".equalsIgnoreCase(normalized);
    }

    private static String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return "assign";
        }
        return actionType.trim().toLowerCase(Locale.ROOT);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
