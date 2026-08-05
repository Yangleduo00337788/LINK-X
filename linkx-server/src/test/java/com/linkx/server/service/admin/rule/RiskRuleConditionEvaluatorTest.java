package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.entity.admin.SysRiskRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiskRuleConditionEvaluator")
class RiskRuleConditionEvaluatorTest {

    private RiskRuleConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RiskRuleConditionEvaluator(new ObjectMapper());
    }

    @Test
    @DisplayName("关键词简配")
    void matchesLegacyKeyword() {
        SysRiskRule rule = SysRiskRule.builder().keyword("spam").build();
        assertTrue(evaluator.matches(rule, RiskRuleContext.builder().text("this is spam text").build()));
        assertFalse(evaluator.matches(rule, RiskRuleContext.builder().text("clean").build()));
    }

    @Test
    @DisplayName("历史风险分条件")
    void matchesHistoryScore() {
        SysRiskRule rule = SysRiskRule.builder()
                .conditionJson("{\"op\":\"and\",\"conditions\":[{\"field\":\"historyScore\",\"op\":\"gte\",\"value\":20}]}")
                .build();
        assertTrue(evaluator.matches(rule, RiskRuleContext.builder().historyScore(25).build()));
        assertFalse(evaluator.matches(rule, RiskRuleContext.builder().historyScore(10).build()));
    }
}
