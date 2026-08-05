package com.linkx.server.service.admin.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeedbackRuleConditionEvaluator")
class FeedbackRuleConditionEvaluatorTest {

    private FeedbackRuleConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        LinkxProperties props = new LinkxProperties();
        props.getApp().setFeedbackSlaHours(24);
        evaluator = new FeedbackRuleConditionEvaluator(new ObjectMapper(), props);
    }

    @Test
    @DisplayName("兼容旧规则 type+keyword")
    void matchesLegacy() {
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .feedbackType("bug")
                .keyword("crash")
                .build();
        Feedback fb = Feedback.builder().type("bug").content("app crash").build();
        assertTrue(evaluator.matches(rule, fb));
        fb.setContent("ok");
        assertFalse(evaluator.matches(rule, fb));
    }

    @Test
    @DisplayName("条件树 AND")
    void matchesConditionTreeAnd() {
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .conditionJson("""
                        {"op":"and","conditions":[
                          {"field":"type","op":"eq","value":"bug"},
                          {"field":"content","op":"contains","value":"payment"}
                        ]}
                        """)
                .build();
        Feedback fb = Feedback.builder().type("bug").content("payment failed").build();
        assertTrue(evaluator.matches(rule, fb));
        fb.setType("suggestion");
        assertFalse(evaluator.matches(rule, fb));
    }

    @Test
    @DisplayName("条件树 OR")
    void matchesConditionTreeOr() {
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .conditionJson("""
                        {"op":"or","conditions":[
                          {"field":"type","op":"eq","value":"bug"},
                          {"field":"type","op":"eq","value":"suggestion"}
                        ]}
                        """)
                .build();
        assertTrue(evaluator.matches(rule, Feedback.builder().type("suggestion").build()));
        assertFalse(evaluator.matches(rule, Feedback.builder().type("other").build()));
    }

    @Test
    @DisplayName("超时条件")
    void matchesOverdue() {
        SysFeedbackDispatchRule rule = SysFeedbackDispatchRule.builder()
                .conditionJson("{\"op\":\"and\",\"conditions\":[{\"field\":\"overdue\",\"op\":\"eq\",\"value\":true}]}")
                .build();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -30);
        Feedback overdue = Feedback.builder()
                .status("pending")
                .createTime(cal.getTime())
                .build();
        assertTrue(evaluator.matches(rule, overdue));

        Feedback fresh = Feedback.builder()
                .status("pending")
                .createTime(new Date())
                .build();
        assertFalse(evaluator.matches(rule, fresh));
    }
}
