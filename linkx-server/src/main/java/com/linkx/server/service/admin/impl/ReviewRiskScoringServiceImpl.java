package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.vo.AdminReviewRiskContextVO;
import com.linkx.server.controller.admin.vo.AdminRiskEventBriefVO;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.admin.rule.RiskRuleContext;
import com.linkx.server.service.admin.rule.RiskRuleEngine;
import com.linkx.server.service.admin.rule.RiskRuleEvaluationResult;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReviewRiskScoringServiceImpl implements ReviewRiskScoringService {

    private final SysRiskEventMapper riskEventMapper;
    private final LinkxProperties linkxProperties;
    private final RiskRuleEngine riskRuleEngine;

    @Override
    public AdminReviewRiskContextVO buildContext(SysReviewTask task) {
        if (task == null) {
            return AdminReviewRiskContextVO.builder()
                    .riskScore(0)
                    .computedRiskLevel("low")
                    .riskFactors(List.of())
                    .recentRiskEventCount24h(0L)
                    .recentHighRiskCount24h(0L)
                    .recentRiskEvents(List.of())
                    .build();
        }
        Long subjectUserId = resolveSubjectUserId(task);
        List<String> factors = new ArrayList<>();
        int score = scoreFromTaskLevel(task.getRiskLevel(), factors);
        int historyScore = computeUserHistoryScore(subjectUserId);
        if (historyScore > 0) {
            factors.add("用户 24h 历史风险 +" + historyScore);
            score = Math.min(100, score + historyScore);
        }
        if (task.getEscalationCount() != null && task.getEscalationCount() > 0) {
            score = Math.min(100, score + 10);
            factors.add("已督办任务 +10");
        }

        RiskRuleContext ruleContext = RiskRuleContext.builder()
                .scope("review")
                .text(task.getContentSnapshot())
                .subjectUserId(subjectUserId)
                .historyScore(historyScore)
                .taskRiskLevel(task.getRiskLevel())
                .escalationCount(task.getEscalationCount())
                .build();
        RiskRuleEvaluationResult ruleResult = riskRuleEngine.evaluate(ruleContext);
        if (ruleResult.getScoreDelta() > 0) {
            score = Math.min(100, score + ruleResult.getScoreDelta());
        }
        factors.addAll(ruleResult.getFactors());

        String computedLevel = scoreToLevel(score);
        String stored = normalizeLevel(task.getRiskLevel());
        if (levelRank(computedLevel) < levelRank(stored)) {
            computedLevel = stored;
        }

        Date since = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
        long recentCount = countEventsSince(subjectUserId, since, null);
        long highCount = countEventsSince(subjectUserId, since, SysRiskEvent.LEVEL_HIGH);
        List<AdminRiskEventBriefVO> recent = listRecentEvents(subjectUserId, 5);

        return AdminReviewRiskContextVO.builder()
                .riskScore(score)
                .computedRiskLevel(computedLevel)
                .riskFactors(factors)
                .recentRiskEventCount24h(recentCount)
                .recentHighRiskCount24h(highCount)
                .recentRiskEvents(recent)
                .build();
    }

    @Override
    public String elevateLevel(String baseLevel, Long subjectUserId) {
        String normalized = normalizeLevel(baseLevel);
        int score = scoreFromTaskLevel(normalized, new ArrayList<>());
        score = Math.min(100, score + computeUserHistoryScore(subjectUserId));
        String computed = scoreToLevel(score);
        return levelRank(computed) >= levelRank(normalized) ? computed : normalized;
    }

    @Override
    public int computeUserHistoryScore(Long subjectUserId) {
        if (subjectUserId == null) {
            return 0;
        }
        Date since = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
        List<SysRiskEvent> events = riskEventMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysRiskEvent::getUserId).eq(subjectUserId)
                        .and(SysRiskEvent::getCreateTime).ge(since)
                        .orderBy(SysRiskEvent::getCreateTime, false)
                        .limit(50)
        );
        if (events.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (SysRiskEvent event : events) {
            String level = normalizeLevel(event.getRiskLevel());
            if (SysRiskEvent.LEVEL_HIGH.equals(level)) {
                score += 20;
            } else if (SysRiskEvent.LEVEL_MEDIUM.equals(level)) {
                score += 10;
            } else {
                score += 5;
            }
        }
        if (events.size() >= 3) {
            score += 15;
        }
        return Math.min(100, score);
    }

    @Override
    public String scoreToLevel(int score) {
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        int criticalMin = policy.getScoreCriticalMin();
        int highMin = policy.getScoreHighMin();
        int mediumMin = policy.getScoreMediumMin();
        if (score >= criticalMin) {
            return "critical";
        }
        if (score >= highMin) {
            return "high";
        }
        if (score >= mediumMin) {
            return "medium";
        }
        return "low";
    }

    private int scoreFromTaskLevel(String level, List<String> factors) {
        String normalized = normalizeLevel(level);
        switch (normalized) {
            case "critical":
                factors.add("任务标记危急");
                return 90;
            case "high":
                factors.add("任务标记高风险");
                return 70;
            case "medium":
                factors.add("任务标记中风险");
                return 50;
            case "low":
                factors.add("任务标记低风险");
                return 20;
            default:
                factors.add("任务未标记风险");
                return 30;
        }
    }

    private long countEventsSince(Long userId, Date since, String minLevel) {
        if (userId == null) {
            return 0;
        }
        QueryWrapper qw = QueryWrapper.create()
                .where(SysRiskEvent::getUserId).eq(userId)
                .and(SysRiskEvent::getCreateTime).ge(since);
        if (StringUtils.hasText(minLevel)) {
            qw.and(SysRiskEvent::getRiskLevel).eq(minLevel);
        }
        return riskEventMapper.selectCountByQuery(qw);
    }

    private List<AdminRiskEventBriefVO> listRecentEvents(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return riskEventMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysRiskEvent::getUserId).eq(userId)
                        .orderBy(SysRiskEvent::getCreateTime, false)
                        .limit(limit)
        ).stream()
                .map(event -> AdminRiskEventBriefVO.builder()
                        .id(event.getId())
                        .eventType(event.getEventType())
                        .title(event.getTitle())
                        .riskLevel(event.getRiskLevel())
                        .status(event.getStatus())
                        .createTime(event.getCreateTime())
                        .build())
                .toList();
    }

    private Long resolveSubjectUserId(SysReviewTask task) {
        if (SysReviewTask.TARGET_USER.equals(task.getTargetType()) && StringUtils.hasText(task.getTargetId())) {
            try {
                return Long.parseLong(task.getTargetId().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "medium";
        }
        return level.trim().toLowerCase(Locale.ROOT);
    }

    private static int levelRank(String level) {
        switch (normalizeLevel(level)) {
            case "critical":
                return 4;
            case "high":
                return 3;
            case "medium":
                return 2;
            case "low":
                return 1;
            default:
                return 0;
        }
    }
}
