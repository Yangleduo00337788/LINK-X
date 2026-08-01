package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端异步导出")
class AdminExportJobIT extends BaseIntegrationTest {

    private static final long ROLE_OPS = 1003L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("创建用户导出任务 → 完成后可下载 CSV")
    void createUsersExportJobAndDownload() throws Exception {
        TestUser ops = registerAndLogin("expadm");
        grantRole(ops.userId, ROLE_OPS);
        ops = login(ops.username, PASSWORD);

        MvcResult created = mockMvc.perform(post("/admin/export-jobs")
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"users\",\"query\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        long jobId = Long.parseLong(objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText());

        String status = waitUntilDone(ops.bearer(), jobId);
        assertTrue("SUCCESS".equals(status), "export status should be SUCCESS, got " + status);

        mockMvc.perform(get("/admin/export-jobs/{id}/download", jobId)
                        .header("Authorization", ops.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")));
    }

    @Test
    @DisplayName("无模块导出权限时创建任务应 403")
    void createWithoutExportPermissionDenied() throws Exception {
        TestUser user = registerAndLogin("expdeny");
        mockMvc.perform(post("/admin/export-jobs")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"users\",\"query\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("运营不可创建风险事件导出任务")
    void opsCannotExportRiskEvents() throws Exception {
        TestUser ops = registerAndLogin("exprisk");
        grantRole(ops.userId, ROLE_OPS);
        ops = login(ops.username, PASSWORD);

        mockMvc.perform(post("/admin/export-jobs")
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"risk-events\",\"query\":{}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String waitUntilDone(String bearer, long jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        String status = "PENDING";
        while (System.currentTimeMillis() < deadline) {
            MvcResult detail = mockMvc.perform(get("/admin/export-jobs/{id}", jobId)
                            .header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            JsonNode data = objectMapper.readTree(detail.getResponse().getContentAsString()).path("data");
            status = data.path("status").asText();
            if ("SUCCESS".equals(status) || "FAILED".equals(status) || "EXPIRED".equals(status)) {
                return status;
            }
            Thread.sleep(200);
        }
        return status;
    }

    private void grantRole(long userId, long roleId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
