package com.linkx.server.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleSlotMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.impl.FeedbackDispatchServiceImpl;
import com.linkx.server.service.admin.rule.FeedbackAssigneeResolver;
import com.linkx.server.service.admin.rule.FeedbackRuleConditionEvaluator;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackDispatchService 自动分流")
class FeedbackDispatchServiceTest {

    @Mock SysFeedbackDispatchRuleMapper ruleMapper;
    @Mock FeedbackMapper feedbackMapper;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock AdminAudienceService adminAudienceService;
    @Mock SysDutyScheduleMapper dutyScheduleMapper;
    @Mock SysDutyScheduleSlotMapper dutyScheduleSlotMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private FeedbackDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        LinkxProperties linkxProperties = new LinkxProperties();
        FeedbackRuleConditionEvaluator conditionEvaluator =
                new FeedbackRuleConditionEvaluator(objectMapper, linkxProperties);
        FeedbackAssigneeResolver assigneeResolver = new FeedbackAssigneeResolver(
                dutyScheduleMapper, dutyScheduleSlotMapper, redisTemplate, objectMapper);
        lenient().when(adminAudienceService.feedbackOperatorUserIds()).thenReturn(List.of(1L));
        service = new FeedbackDispatchServiceImpl(
                ruleMapper, feedbackMapper, conditionEvaluator, assigneeResolver, adminEventPublisher, adminAudienceService);
    }

    @Test
    @DisplayName("按优先级匹配类型+关键词")
    void matchAssignee_priorityAndConditions() {
        SysFeedbackDispatchRule low = rule("low", "bug", "crash", 10L, 1);
        SysFeedbackDispatchRule high = rule("high", "bug", null, 20L, 10);
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(high, low));

        Feedback fb = Feedback.builder().type("bug").content("app crash on login").build();
        assertEquals(20L, service.matchAssignee(fb).orElseThrow());

        fb.setType("suggestion");
        assertTrue(service.matchAssignee(fb).isEmpty());
    }

    @Test
    @DisplayName("新建反馈后自动指派")
    void applyAutoDispatch_updatesFeedback() {
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(rule("r1", "bug", null, 99L, 1)));

        Feedback fb = Feedback.builder().id(1L).type("bug").content("x").build();
        service.applyAutoDispatch(fb);

        assertEquals(99L, fb.getAssigneeId());
        assertNotNull(fb.getAssignedAt());
        verify(feedbackMapper).update(fb);
        verify(adminEventPublisher).publishToUsers(eq("feedback_assigned"), eq(1L), eq(List.of(1L)), any());
    }

    @Test
    @DisplayName("已有指派或缺少 ID 时跳过")
    void applyAutoDispatch_skipWhenAssigned() {
        Feedback fb = Feedback.builder().id(1L).assigneeId(5L).type("bug").build();
        service.applyAutoDispatch(fb);
        verifyNoInteractions(ruleMapper);
        verify(feedbackMapper, never()).update(any());
    }

    @Test
    @DisplayName("无匹配规则时不更新")
    void applyAutoDispatch_noRule() {
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        Feedback fb = Feedback.builder().id(2L).type("other").content("hello").build();
        service.applyAutoDispatch(fb);
        verify(feedbackMapper, never()).update(any());
    }

    @Test
    @DisplayName("tryReassign 匹配到新处理人时改派")
    void tryReassign_updatesWhenDifferent() {
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(rule("r1", "bug", null, 88L, 1)));

        Feedback fb = Feedback.builder().id(3L).assigneeId(10L).type("bug").content("x").build();
        assertTrue(service.tryReassign(fb));
        assertEquals(88L, fb.getAssigneeId());
        verify(feedbackMapper).update(fb);
    }

    @Test
    @DisplayName("tryReassign 无新处理人时跳过")
    void tryReassign_skipWhenSame() {
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(rule("r1", "bug", null, 10L, 1)));

        Feedback fb = Feedback.builder().id(4L).assigneeId(10L).type("bug").content("x").build();
        assertFalse(service.tryReassign(fb));
        verify(feedbackMapper, never()).update(any());
    }

    @Test
    @DisplayName("仅通知动作可命中")
    void evaluate_notifyOnly() {
        SysFeedbackDispatchRule notifyRule = SysFeedbackDispatchRule.builder()
                .id(100L)
                .name("notify-ops")
                .feedbackType("bug")
                .actionType("notify")
                .notifyRoles("ops")
                .priority(5)
                .enabled(true)
                .deleted(0)
                .build();
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(notifyRule));

        Feedback fb = Feedback.builder().type("bug").content("x").build();
        var result = service.evaluate(fb);
        assertTrue(result.isPresent());
        assertNull(result.get().getAssigneeId());
        assertEquals("notify", result.get().getActionType());
    }

    private static SysFeedbackDispatchRule rule(String name, String type, String keyword, Long assignee, int priority) {
        return SysFeedbackDispatchRule.builder()
                .name(name)
                .feedbackType(type)
                .keyword(keyword)
                .assigneeId(assignee)
                .assigneeSource("fixed")
                .actionType("assign")
                .priority(priority)
                .enabled(true)
                .deleted(0)
                .build();
    }
}
