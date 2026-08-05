package com.linkx.server.service.admin.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.admin.dto.AdminApprovalStartDTO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysApprovalFlow;
import com.linkx.server.entity.admin.SysApprovalInstance;
import com.linkx.server.entity.admin.SysApprovalRecord;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.SysApprovalFlowMapper;
import com.linkx.server.mapper.admin.SysApprovalInstanceMapper;
import com.linkx.server.mapper.admin.SysApprovalRecordMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.ApprovalTempGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalFlowEngine")
class ApprovalFlowEngineTest {

    @Mock SysApprovalFlowMapper flowMapper;
    @Mock SysApprovalInstanceMapper instanceMapper;
    @Mock SysApprovalRecordMapper recordMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysUserRoleMapper sysUserRoleMapper;
    @Mock SysReviewTaskMapper reviewTaskMapper;
    @Mock AdminEventPublisher adminEventPublisher;
    @Mock ApprovalTempGrantService approvalTempGrantService;
    @Mock MessageNotificationService notificationService;
    @Mock EmailService emailService;
    @Mock AdminReviewService adminReviewService;

    private ApprovalFlowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ApprovalFlowEngine(
                flowMapper,
                instanceMapper,
                recordMapper,
                sysUserMapper,
                sysUserRoleMapper,
                reviewTaskMapper,
                adminEventPublisher,
                approvalTempGrantService,
                notificationService,
                emailService,
                new ObjectMapper(),
                adminReviewService);
    }

    @Test
    @DisplayName("单级审批通过后实例完成")
    void singleApproveCompletesInstance() {
        SysApprovalFlow flow = SysApprovalFlow.builder()
                .id(1L)
                .name("审核一审")
                .enabled(true)
                .stepsJson("""
                        [{"name":"初审","nodeType":"approve","assigneeType":"user","assigneeId":"100"}]
                        """)
                .build();
        when(flowMapper.selectOneById(1L)).thenReturn(flow);
        when(instanceMapper.selectCountByQuery(any())).thenReturn(0L);

        SysApprovalInstance instance = SysApprovalInstance.builder()
                .id(10L)
                .flowId(1L)
                .flowName(flow.getName())
                .bizType("generic")
                .bizId("biz-1")
                .title("测试审批")
                .status(SysApprovalInstance.STATUS_PENDING)
                .currentStep(0)
                .applicantId(200L)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        when(instanceMapper.insert(any())).thenAnswer(inv -> {
            SysApprovalInstance ins = inv.getArgument(0);
            ins.setId(instance.getId());
            return 1;
        });
        when(instanceMapper.selectOneById(10L)).thenReturn(instance);

        List<SysApprovalRecord> records = new ArrayList<>();
        when(recordMapper.insert(any())).thenAnswer(inv -> {
            SysApprovalRecord r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(20L);
            }
            records.clear();
            records.add(r);
            return 1;
        });
        when(recordMapper.selectOneById(20L)).thenAnswer(inv ->
                records.stream().filter(r -> Long.valueOf(20L).equals(r.getId())).findFirst().orElse(null));
        when(recordMapper.selectListByQuery(any())).thenAnswer(inv -> List.copyOf(records));

        when(sysUserMapper.selectOneById(any())).thenReturn(SysUser.builder().id(100L).username("auditor").build());

        AdminApprovalStartDTO start = new AdminApprovalStartDTO();
        start.setFlowId("1");
        start.setBizType("generic");
        start.setBizId("biz-1");
        start.setTitle("测试审批");
        engine.start(start, 200L);

        engine.approveRecord(20L, null, 100L);

        ArgumentCaptor<SysApprovalInstance> captor = ArgumentCaptor.forClass(SysApprovalInstance.class);
        verify(instanceMapper).update(captor.capture());
        assertEquals(SysApprovalInstance.STATUS_APPROVED, captor.getValue().getStatus());
    }
}
