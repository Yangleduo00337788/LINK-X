package com.linkx.server.controller.admin;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.SysApprovalFlow;
import com.linkx.server.entity.admin.SysApprovalInstance;
import com.linkx.server.entity.admin.SysApprovalRecord;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.SysApprovalFlowMapper;
import com.linkx.server.mapper.admin.SysApprovalInstanceMapper;
import com.linkx.server.mapper.admin.SysApprovalRecordMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.ApprovalTempGrantService;
import com.linkx.server.support.BaseIntegrationTest;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("审批待办与临时授权")
class AdminApprovalIT extends BaseIntegrationTest {

    private static final long ROLE_OPS = 1003L;
    private static final long ROLE_READONLY = 1006L;

    @Autowired private SysApprovalFlowMapper flowMapper;
    @Autowired private SysApprovalInstanceMapper instanceMapper;
    @Autowired private SysApprovalRecordMapper recordMapper;
    @Autowired private SysUserRoleMapper sysUserRoleMapper;
    @Autowired private RbacService rbacService;
    @Autowired private ApprovalTempGrantService approvalTempGrantService;

    @Test
    @DisplayName("非审核角色经临时授权可完成三级审批")
    void tempGrantThreeStepApproval() throws Exception {
        TestUser security = promoteAndRelogin("secap", AdminConstants.ROLE_SECURITY_ADMIN);
        TestUser readonly = promoteAndRelogin("roap", AdminConstants.ROLE_READONLY_OBSERVER);
        TestUser ops = promoteAndRelogin("opsap", AdminConstants.ROLE_OPS_ADMIN);

        long flowId = seedFlow(security.userId, readonly.userId, ops.userId);
        long instanceId = seedInstance(flowId);
        long initialRecordId =
                seedRecord(instanceId, 0, "初审", SysApprovalRecord.NODE_APPROVE, security.userId);
        approvalTempGrantService.grantForRecord(initialRecordId, security.userId);

        mockMvc.perform(get("/admin/approvals/inbox")
                        .header("Authorization", security.bearer())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/admin/approvals/records/" + initialRecordId + "/approve")
                        .header("Authorization", security.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"初审通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        SysApprovalInstance instance = instanceMapper.selectOneById(instanceId);
        assertEquals(1, instance.getCurrentStep());

        long readonlyRecordId = pendingRecordId(instanceId, readonly.userId);
        long opsRecordId = pendingRecordId(instanceId, ops.userId);
        assertTrue(readonlyRecordId > 0);
        assertTrue(opsRecordId > 0);

        mockMvc.perform(post("/admin/approvals/records/" + readonlyRecordId + "/approve")
                        .header("Authorization", readonly.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"会签1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/approvals/records/" + opsRecordId + "/approve")
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"会签2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        instance = instanceMapper.selectOneById(instanceId);
        assertEquals(SysApprovalInstance.STATUS_APPROVED, instance.getStatus());
    }

    private TestUser promoteAndRelogin(String prefix, String expectedRole) {
        long roleId = roleIdFor(expectedRole);
        TestUser user = registerAndLogin(prefix);
        grantRole(user.userId, roleId);
        assertTrue(rbacService.getUserRoleCodes(user.userId).contains(expectedRole));
        return login(user.username, "Test1234abcd");
    }

    private static long roleIdFor(String roleCode) {
        return switch (roleCode) {
            case AdminConstants.ROLE_OPS_ADMIN -> ROLE_OPS;
            case AdminConstants.ROLE_READONLY_OBSERVER -> ROLE_READONLY;
            case AdminConstants.ROLE_SECURITY_ADMIN -> 1005L;
            default -> throw new IllegalArgumentException(roleCode);
        };
    }

    private void grantRole(long userId, long roleId) {
        sysUserRoleMapper.insert(
                SysUserRole.builder().userId(userId).roleId(roleId).createBy(null).deleted(0).build());
        rbacService.evictUserCache(userId);
    }

    private long seedFlow(long securityUserId, long readonlyUserId, long opsUserId) {
        SysApprovalFlow flow =
                SysApprovalFlow.builder()
                        .name("IT三级审批")
                        .bizType("review")
                        .description("integration test")
                        .stepsJson(
                                """
                                [
                                  {"name":"初审","nodeType":"approve","assigneeType":"user","assigneeId":"%d"},
                                  {"name":"会签","nodeType":"countersign","assigneeType":"user","assigneeIds":["%d","%d"]},
                                  {"name":"抄送","nodeType":"cc","assigneeType":"user","assigneeId":"1"}
                                ]
                                """
                                        .formatted(securityUserId, readonlyUserId, opsUserId))
                        .enabled(true)
                        .autoStart(false)
                        .priority(0)
                        .deleted(0)
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();
        flowMapper.insert(flow);
        return flow.getId();
    }

    private long seedInstance(long flowId) {
        SysApprovalInstance instance =
                SysApprovalInstance.builder()
                        .flowId(flowId)
                        .flowName("IT三级审批")
                        .bizType("generic")
                        .bizId("it-biz-1")
                        .title("违规项: IT测试")
                        .status(SysApprovalInstance.STATUS_PENDING)
                        .currentStep(0)
                        .applicantId(1L)
                        .applicantName("admin")
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();
        instanceMapper.insert(instance);
        return instance.getId();
    }

    private long seedRecord(
            long instanceId, int stepIndex, String stepName, String nodeType, long assigneeId) {
        SysApprovalRecord record =
                SysApprovalRecord.builder()
                        .instanceId(instanceId)
                        .stepIndex(stepIndex)
                        .stepName(stepName)
                        .nodeType(nodeType)
                        .assigneeId(assigneeId)
                        .assigneeName("assignee")
                        .status(SysApprovalRecord.STATUS_PENDING)
                        .createTime(new Date())
                        .build();
        recordMapper.insert(record);
        return record.getId();
    }

    private long pendingRecordId(long instanceId, long assigneeId) {
        List<SysApprovalRecord> records =
                recordMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(SysApprovalRecord::getInstanceId)
                                .eq(instanceId)
                                .and(SysApprovalRecord::getAssigneeId)
                                .eq(assigneeId)
                                .and(SysApprovalRecord::getStatus)
                                .eq(SysApprovalRecord.STATUS_PENDING));
        return records.isEmpty() ? -1L : records.getFirst().getId();
    }
}
