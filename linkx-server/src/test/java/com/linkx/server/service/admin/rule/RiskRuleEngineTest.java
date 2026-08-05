package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.entity.admin.SysRiskRule;
import com.linkx.server.mapper.admin.SysRiskRuleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskRuleEngine")
class RiskRuleEngineTest {

    @Mock SysRiskRuleMapper riskRuleMapper;

    private RiskRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskRuleEngine(riskRuleMapper, new RiskRuleConditionEvaluator(new ObjectMapper()));
    }

    @Test
    @DisplayName("累加命中规则分数")
    void evaluate_accumulatesScore() {
        SysRiskRule rule1 = SysRiskRule.builder()
                .id(1L)
                .name("spam")
                .scope("simulate")
                .keyword("spam")
                .scoreDelta(15)
                .actionType("score_only")
                .enabled(true)
                .deleted(0)
                .build();
        SysRiskRule rule2 = SysRiskRule.builder()
                .id(2L)
                .name("block-spam")
                .scope("global")
                .keyword("spam")
                .scoreDelta(20)
                .actionType("block")
                .enabled(true)
                .deleted(0)
                .build();
        when(riskRuleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(rule1, rule2));

        RiskRuleEvaluationResult result = engine.evaluate(
                RiskRuleContext.builder().scope("simulate").text("spam message").build());

        assertEquals(35, result.getScoreDelta());
        assertTrue(result.isBlocked());
        assertEquals(2, result.getMatchedRules().size());
    }
}
