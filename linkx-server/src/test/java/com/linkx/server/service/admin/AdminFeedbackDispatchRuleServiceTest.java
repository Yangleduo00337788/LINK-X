package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchRuleDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysFeedbackDispatchRuleMapper;
import com.linkx.server.service.admin.impl.AdminFeedbackDispatchRuleServiceImpl;
import com.linkx.server.service.admin.rule.FeedbackAssigneeResolver;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeedbackDispatchRuleService 分流规则")
class AdminFeedbackDispatchRuleServiceTest {

    @Mock SysFeedbackDispatchRuleMapper ruleMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysDutyScheduleMapper dutyScheduleMapper;
    @Mock FeedbackAssigneeResolver assigneeResolver;
    @Mock FeedbackDispatchService feedbackDispatchService;

    private AdminFeedbackDispatchRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminFeedbackDispatchRuleServiceImpl(
                ruleMapper, sysUserMapper, dutyScheduleMapper, assigneeResolver, feedbackDispatchService);
    }

    @Test
    @DisplayName("CRUD 基本流程")
    void crud_ok() {
        when(sysUserMapper.selectOneById(7L)).thenReturn(SysUser.builder().id(7L).username("ops").build());
        doNothing().when(assigneeResolver).validateRuleAssignee(any());
        when(ruleMapper.selectOneById(1L)).thenAnswer(inv -> entity(1L));

        AdminFeedbackDispatchRuleDTO dto = new AdminFeedbackDispatchRuleDTO();
        dto.setName(" bug rule ");
        dto.setFeedbackType("bug");
        dto.setKeyword("crash");
        dto.setAssigneeId(7L);
        dto.setPriority(5);
        dto.setEnabled(true);

        var created = service.create(dto, 9L);
        assertEquals("bug rule", created.getName());
        verify(ruleMapper).insert(any(SysFeedbackDispatchRule.class));

        var updated = service.update(1L, dto, 9L);
        assertEquals("ops", updated.getAssigneeName());
        verify(ruleMapper).update(any(SysFeedbackDispatchRule.class));

        service.delete(1L, 9L);
        verify(ruleMapper, atLeastOnce()).update(any(SysFeedbackDispatchRule.class));
    }

    @Test
    @DisplayName("列表分页")
    void list_ok() {
        when(ruleMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(ruleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(entity(2L)));
        when(sysUserMapper.selectOneById(7L)).thenReturn(SysUser.builder().id(7L).nickname("运营").build());

        var page = service.list(new AdminPageQueryDTO());
        assertEquals(1, page.getTotal());
        assertEquals("运营", page.getItems().get(0).getAssigneeName());
    }

    @Test
    @DisplayName("无效处理人")
    void create_invalidAssignee() {
        AdminFeedbackDispatchRuleDTO dto = new AdminFeedbackDispatchRuleDTO();
        dto.setName("r");
        dto.setAssigneeId(99L);
        doThrow(new CustomException(400, "assignee not found"))
                .when(assigneeResolver).validateRuleAssignee(any());
        assertThrows(CustomException.class, () -> service.create(dto, 1L));
    }

    private SysFeedbackDispatchRule entity(Long id) {
        return SysFeedbackDispatchRule.builder()
                .id(id)
                .name("rule")
                .feedbackType("bug")
                .assigneeId(7L)
                .assigneeSource("fixed")
                .actionType("assign")
                .priority(1)
                .enabled(true)
                .deleted(0)
                .build();
    }
}
