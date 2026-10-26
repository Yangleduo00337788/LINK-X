package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminRiskPolicySimulateDTO;
import com.linkx.server.controller.admin.dto.AdminRiskPolicyUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicySimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicyVO;
import com.linkx.server.entity.SysRuntimeSetting;
import com.linkx.server.entity.SysSensitiveWord;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysRuntimeSettingMapper;
import com.linkx.server.service.AiContentReviewService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminRiskPolicyService;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.admin.rule.RiskRuleContext;
import com.linkx.server.service.admin.rule.RiskRuleEngine;
import com.linkx.server.service.admin.rule.RiskRuleEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminRiskPolicyServiceImpl implements AdminRiskPolicyService {

    private final LinkxProperties linkxProperties;
    private final SysRuntimeSettingMapper runtimeSettingMapper;
    private final SensitiveWordService sensitiveWordService;
    private final AiContentReviewService aiContentReviewService;
    private final ReviewRiskScoringService reviewRiskScoringService;
    private final RiskRuleEngine riskRuleEngine;

    @Override
    public AdminRiskPolicyVO getOverview() {
        return buildOverview();
    }

    @Override
    @Transactional
    public AdminRiskPolicyVO update(AdminRiskPolicyUpdateDTO dto, Long operatorId) {
        if (dto == null) {
            throw new CustomException(400, "请求体不能为空");
        }
        SysRuntimeSetting row = loadOrCreateRow(operatorId);
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        LinkxProperties.Auth auth = linkxProperties.getAuth();

        if (dto.getMessageStormUserThreshold() != null) {
            policy.setMessageStormUserThreshold(dto.getMessageStormUserThreshold());
            row.setRiskStormUserThreshold(dto.getMessageStormUserThreshold());
        }
        if (dto.getMessageStormUserWindowSeconds() != null) {
            policy.setMessageStormUserWindowSeconds(dto.getMessageStormUserWindowSeconds());
            row.setRiskStormUserWindowSeconds(dto.getMessageStormUserWindowSeconds());
        }
        if (dto.getMessageStormGroupMinMembers() != null) {
            policy.setMessageStormGroupMinMembers(dto.getMessageStormGroupMinMembers());
            row.setRiskStormGroupMinMembers(dto.getMessageStormGroupMinMembers());
        }
        if (dto.getMessageStormGroupLargeMembers() != null) {
            policy.setMessageStormGroupLargeMemberThreshold(dto.getMessageStormGroupLargeMembers());
            row.setRiskStormGroupLargeMembers(dto.getMessageStormGroupLargeMembers());
        }
        if (dto.getMessageStormGroupMidPerMinute() != null) {
            policy.setMessageStormGroupMidMaxPerMinute(dto.getMessageStormGroupMidPerMinute());
            row.setRiskStormGroupMidPerMinute(dto.getMessageStormGroupMidPerMinute());
        }
        if (dto.getMessageStormGroupLargePerMinute() != null) {
            policy.setMessageStormGroupLargeMaxPerMinute(dto.getMessageStormGroupLargePerMinute());
            row.setRiskStormGroupLargePerMinute(dto.getMessageStormGroupLargePerMinute());
        }
        if (dto.getScoreMediumMin() != null) {
            policy.setScoreMediumMin(dto.getScoreMediumMin());
            row.setRiskScoreMediumMin(dto.getScoreMediumMin());
        }
        if (dto.getScoreHighMin() != null) {
            policy.setScoreHighMin(dto.getScoreHighMin());
            row.setRiskScoreHighMin(dto.getScoreHighMin());
        }
        if (dto.getScoreCriticalMin() != null) {
            policy.setScoreCriticalMin(dto.getScoreCriticalMin());
            row.setRiskScoreCriticalMin(dto.getScoreCriticalMin());
        }
        if (dto.getAiReviewEnabled() != null) {
            policy.setAiReviewEnabled(dto.getAiReviewEnabled());
            row.setAiReviewEnabled(dto.getAiReviewEnabled());
        }
        if (dto.getAiReviewHumanScoreMin() != null) {
            policy.setAiReviewHumanScoreMin(dto.getAiReviewHumanScoreMin());
            row.setAiReviewHumanScoreMin(dto.getAiReviewHumanScoreMin());
        }
        if (dto.getRateLimitLoginPerMinute() != null) {
            auth.setRateLimitLoginPerMinute(dto.getRateLimitLoginPerMinute());
            row.setRateLimitLoginPerMinute(dto.getRateLimitLoginPerMinute());
        }
        if (dto.getRateLimitRegisterPerMinute() != null) {
            auth.setRateLimitRegisterPerMinute(dto.getRateLimitRegisterPerMinute());
            row.setRateLimitRegisterPerMinute(dto.getRateLimitRegisterPerMinute());
        }
        if (dto.getRateLimitSearchPerMinute() != null) {
            auth.setRateLimitSearchPerMinute(dto.getRateLimitSearchPerMinute());
            row.setRateLimitSearchPerMinute(dto.getRateLimitSearchPerMinute());
        }
        if (dto.getRateLimitListPerMinute() != null) {
            auth.setRateLimitListPerMinute(dto.getRateLimitListPerMinute());
            row.setRateLimitListPerMinute(dto.getRateLimitListPerMinute());
        }
        if (dto.getRateLimitWritePerMinute() != null) {
            auth.setRateLimitWritePerMinute(dto.getRateLimitWritePerMinute());
            row.setRateLimitWritePerMinute(dto.getRateLimitWritePerMinute());
        }
        if (dto.getRateLimitUploadPerMinute() != null) {
            auth.setRateLimitUploadPerMinute(dto.getRateLimitUploadPerMinute());
            row.setRateLimitUploadPerMinute(dto.getRateLimitUploadPerMinute());
        }

        validateScoreThresholds(policy);
        persist(row);
        return buildOverview();
    }

    @Override
    public AdminRiskPolicySimulateVO simulate(AdminRiskPolicySimulateDTO dto) {
        String text = dto == null || dto.getText() == null ? "" : dto.getText();
        boolean wordEnabled = !Boolean.FALSE.equals(linkxProperties.getApp().getSensitiveFilterEnabled());
        boolean aiEnabled = aiContentReviewService.isEnabled();
        int humanMin = aiContentReviewService.humanScoreMin();
        SensitiveWordService.FilterResult filterResult = sensitiveWordService.filter(text);

        List<String> factors = new ArrayList<>();
        int score = 0;
        String aiReason = null;
        List<String> aiLabels = List.of();
        boolean queued = false;
        if (aiEnabled) {
            AiContentReviewService.ReviewScore ai = aiContentReviewService.review(text);
            score = ai.score();
            queued = ai.queuedForHuman();
            aiReason = ai.reason();
            aiLabels = ai.labels();
            factors.add("AI 机审 " + ai.score() + " 分");
            if (queued) {
                factors.add("超过人审上限 " + humanMin + "，将送人审");
            } else {
                factors.add("未超过人审上限 " + humanMin + "，自动放行");
            }
        } else {
            factors.add("AI 机审未开启");
        }
        if (!filterResult.matchedWords().isEmpty()) {
            factors.add("词库提示命中 " + filterResult.matchedWords().size() + " 个");
        }
        int historyScore = reviewRiskScoringService.computeUserHistoryScore(dto != null ? dto.getSubjectUserId() : null);
        if (historyScore > 0) {
            factors.add("用户历史风险 +" + historyScore);
            score = Math.min(100, score + historyScore);
            queued = score >= humanMin;
        }

        RiskRuleContext ruleContext = RiskRuleContext.builder()
                .scope("simulate")
                .text(text)
                .subjectUserId(dto != null ? dto.getSubjectUserId() : null)
                .historyScore(historyScore)
                .messageCount(dto != null ? dto.getMessageCount() : null)
                .memberCount(dto != null ? dto.getMemberCount() : null)
                .sensitiveBlocked(filterResult.blocked())
                .sensitiveAlerted(filterResult.alerted())
                .sensitiveFiltered(filterResult.filtered())
                .build();
        RiskRuleEvaluationResult ruleResult = riskRuleEngine.evaluate(ruleContext);
        if (ruleResult.getScoreDelta() > 0) {
            score = Math.min(100, score + ruleResult.getScoreDelta());
            queued = score >= humanMin;
        }
        factors.addAll(ruleResult.getFactors());

        String level = reviewRiskScoringService.scoreToLevel(score);
        List<AdminRiskPolicySimulateVO.MatchedWordDetail> details = buildMatchedDetails(filterResult.matchedWords());

        return AdminRiskPolicySimulateVO.builder()
                .sensitiveFilterEnabled(wordEnabled)
                .aiReviewEnabled(aiEnabled)
                .aiReviewHumanScoreMin(humanMin)
                .queuedForHuman(queued)
                .aiReason(aiReason)
                .aiLabels(aiLabels)
                .blocked(false)
                .filtered(false)
                .alerted(queued)
                .filteredText(text)
                .matchedWords(filterResult.matchedWords())
                .matchedDetails(details)
                .riskScore(score)
                .riskLevel(level)
                .riskFactors(factors)
                .ruleScoreDelta(ruleResult.getScoreDelta())
                .ruleBlocked(ruleResult.isBlocked())
                .ruleAlerted(ruleResult.isAlerted())
                .matchedRules(buildMatchedRuleDetails(ruleResult))
                .build();
    }

    private List<AdminRiskPolicySimulateVO.MatchedRuleDetail> buildMatchedRuleDetails(
            RiskRuleEvaluationResult ruleResult) {
        if (ruleResult.getMatchedRules() == null || ruleResult.getMatchedRules().isEmpty()) {
            return List.of();
        }
        List<AdminRiskPolicySimulateVO.MatchedRuleDetail> out = new ArrayList<>();
        for (RiskRuleEvaluationResult.MatchedRule rule : ruleResult.getMatchedRules()) {
            out.add(AdminRiskPolicySimulateVO.MatchedRuleDetail.builder()
                    .ruleId(rule.getRuleId())
                    .ruleName(rule.getRuleName())
                    .scoreDelta(rule.getScoreDelta())
                    .actionType(rule.getActionType())
                    .build());
        }
        return out;
    }

    private AdminRiskPolicyVO buildOverview() {
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return AdminRiskPolicyVO.builder()
                .messageStorm(AdminRiskPolicyVO.MessageStormPolicy.builder()
                        .userThreshold(policy.getMessageStormUserThreshold())
                        .userWindowSeconds(policy.getMessageStormUserWindowSeconds())
                        .groupMinMembers(policy.getMessageStormGroupMinMembers())
                        .groupLargeMembers(policy.getMessageStormGroupLargeMemberThreshold())
                        .groupMidPerMinute(policy.getMessageStormGroupMidMaxPerMinute())
                        .groupLargePerMinute(policy.getMessageStormGroupLargeMaxPerMinute())
                        .build())
                .scoreThresholds(AdminRiskPolicyVO.ScoreThresholdPolicy.builder()
                        .mediumMin(policy.getScoreMediumMin())
                        .highMin(policy.getScoreHighMin())
                        .criticalMin(policy.getScoreCriticalMin())
                        .build())
                .rateLimits(AdminRiskPolicyVO.RateLimitPolicy.builder()
                        .loginPerMinute(auth.getRateLimitLoginPerMinute())
                        .registerPerMinute(auth.getRateLimitRegisterPerMinute())
                        .searchPerMinute(auth.getRateLimitSearchPerMinute())
                        .listPerMinute(auth.getRateLimitListPerMinute())
                        .writePerMinute(auth.getRateLimitWritePerMinute())
                        .uploadPerMinute(auth.getRateLimitUploadPerMinute())
                        .build())
                .loginLock(AdminRiskPolicyVO.LoginLockPolicy.builder()
                        .clientMaxAttempts(auth.getLoginMaxAttempts())
                        .clientLockMinutes(auth.getLockDurationMinutes())
                        .adminMaxAttempts(auth.getAdminLoginMaxAttempts())
                        .adminLockMinutes(auth.getAdminLockDurationMinutes())
                        .build())
                        .sensitiveFilterEnabled(!Boolean.FALSE.equals(linkxProperties.getApp().getSensitiveFilterEnabled()))
                .aiReviewEnabled(!Boolean.FALSE.equals(policy.getAiReviewEnabled()))
                .aiReviewHumanScoreMin(policy.getAiReviewHumanScoreMin())
                .build();
    }

    private List<AdminRiskPolicySimulateVO.MatchedWordDetail> buildMatchedDetails(List<String> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<AdminRiskPolicySimulateVO.MatchedWordDetail> out = new ArrayList<>();
        for (String word : words) {
            out.add(AdminRiskPolicySimulateVO.MatchedWordDetail.builder()
                    .word(word)
                    .action(sensitiveWordService.actionForWord(word))
                    .build());
        }
        return out;
    }

    private void validateScoreThresholds(LinkxProperties.RiskPolicy policy) {
        if (policy.getScoreMediumMin() >= policy.getScoreHighMin()
                || policy.getScoreHighMin() >= policy.getScoreCriticalMin()) {
            throw new CustomException(400, "风险分数线须满足：中 < 高 < 危急");
        }
    }

    private SysRuntimeSetting loadOrCreateRow(Long operatorId) {
        SysRuntimeSetting existing = runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID);
        if (existing != null) {
            return existing;
        }
        return SysRuntimeSetting.builder()
                .id(SysRuntimeSetting.SINGLETON_ID)
                .updateBy(operatorId)
                .build();
    }

    private void persist(SysRuntimeSetting row) {
        SysRuntimeSetting existing = runtimeSettingMapper.selectOneById(SysRuntimeSetting.SINGLETON_ID);
        if (existing == null) {
            runtimeSettingMapper.insert(row);
        } else {
            runtimeSettingMapper.update(row);
        }
    }
}
