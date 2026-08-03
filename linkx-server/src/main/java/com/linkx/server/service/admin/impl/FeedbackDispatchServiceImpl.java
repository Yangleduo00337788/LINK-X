package com.linkx.server.service.admin.impl;

import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.FeedbackDispatchService;
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

    @Override
    public Optional<Long> matchAssignee(Feedback feedback) {
        if (feedback == null) {
            return Optional.empty();
        }
        List<SysFeedbackDispatchRule> rules = ruleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysFeedbackDispatchRule::getDeleted).eq(0)
                        .and(SysFeedbackDispatchRule::getEnabled).eq(true)
                        .orderBy(SysFeedbackDispatchRule::getPriority, false)
                        .orderBy(SysFeedbackDispatchRule::getUpdateTime, false));
        for (SysFeedbackDispatchRule rule : rules) {
            if (matches(rule, feedback) && rule.getAssigneeId() != null) {
                return Optional.of(rule.getAssigneeId());
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
        matchAssignee(feedback).ifPresent(assigneeId -> {
            feedback.setAssigneeId(assigneeId);
            feedback.setAssignedAt(new Date());
            feedbackMapper.update(feedback);
        });
    }

    static boolean matches(SysFeedbackDispatchRule rule, Feedback feedback) {
        if (rule == null || feedback == null) {
            return false;
        }
        if (StringUtils.hasText(rule.getFeedbackType())) {
            String expected = rule.getFeedbackType().trim().toLowerCase(Locale.ROOT);
            String actual = feedback.getType() == null ? "" : feedback.getType().trim().toLowerCase(Locale.ROOT);
            if (!expected.equals(actual)) {
                return false;
            }
        }
        if (StringUtils.hasText(rule.getKeyword())) {
            String content = feedback.getContent() == null ? "" : feedback.getContent().toLowerCase(Locale.ROOT);
            if (!content.contains(rule.getKeyword().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
