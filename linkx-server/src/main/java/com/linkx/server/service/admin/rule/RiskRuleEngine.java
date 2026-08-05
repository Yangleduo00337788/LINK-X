package com.linkx.server.service.admin.rule;

import com.linkx.server.entity.admin.SysRiskRule;
import com.linkx.server.mapper.admin.SysRiskRuleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RiskRuleEngine {

    private final SysRiskRuleMapper riskRuleMapper;
    private final RiskRuleConditionEvaluator conditionEvaluator;

    public RiskRuleEvaluationResult evaluate(RiskRuleContext context) {
        if (context == null) {
            return RiskRuleEvaluationResult.builder().build();
        }
        String scope = normalizeScope(context.getScope());
        List<SysRiskRule> rules = loadEnabledRules(scope);
        RiskRuleEvaluationResult result = RiskRuleEvaluationResult.builder().build();
        for (SysRiskRule rule : rules) {
            if (!conditionEvaluator.matches(rule, context)) {
                continue;
            }
            int delta = rule.getScoreDelta() == null ? 0 : rule.getScoreDelta();
            result.setScoreDelta(result.getScoreDelta() + delta);
            String actionType = normalizeActionType(rule.getActionType());
            if ("block".equals(actionType)) {
                result.setBlocked(true);
            }
            if ("alert".equals(actionType) || "escalate".equals(actionType)) {
                result.setAlerted(true);
            }
            String factor = rule.getName() + " +" + delta;
            if (!"score_only".equals(actionType)) {
                factor += " (" + actionType + ")";
            }
            result.getFactors().add(factor);
            result.getMatchedRules().add(RiskRuleEvaluationResult.MatchedRule.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .scoreDelta(delta)
                    .actionType(actionType)
                    .build());
        }
        return result;
    }

    private List<SysRiskRule> loadEnabledRules(String scope) {
        QueryWrapper qw = QueryWrapper.create()
                .where(SysRiskRule::getDeleted).eq(0)
                .and(SysRiskRule::getEnabled).eq(true);
        qw.and((QueryWrapper w) -> {
            w.where(SysRiskRule::getScope).eq("global")
                    .or(SysRiskRule::getScope).eq(scope);
        });
        qw.orderBy(SysRiskRule::getPriority, false)
                .orderBy(SysRiskRule::getUpdateTime, false);
        List<SysRiskRule> rules = riskRuleMapper.selectListByQuery(qw);
        return rules == null ? new ArrayList<>() : rules;
    }

    private static String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "global";
        }
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return "score_only";
        }
        return actionType.trim().toLowerCase(Locale.ROOT);
    }
}
