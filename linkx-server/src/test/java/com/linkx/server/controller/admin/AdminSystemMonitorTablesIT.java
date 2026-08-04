package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("系统监控-表体量 API")
class AdminSystemMonitorTablesIT extends BaseIntegrationTest {

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("tableList 数组长度应与 storage.tableCount 一致")
    void tablesLengthMatchesStorageCount() throws Exception {
        TestUser user = registerAndLogin("tbl");
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(user.userId)
                .roleId(1001L)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(user.userId);
        TestUser admin = login(user.username, "Test1234abcd");

        MvcResult result = mockMvc.perform(get("/admin/system-monitor/tables")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.isObject(), "data should be object");

        JsonNode tables = data.path("tableList");
        assertTrue(tables.isArray(), "tableList should be array, got: " + tables.getNodeType() + " body=" + body);

        int tableCount = data.path("storage").path("tableCount").asInt(-1);
        assertEquals(tableCount, tables.size(), "storage.tableCount must match tableList.length");
        assertTrue(rbacService.getUserRoleCodes(admin.userId).contains(AdminConstants.ROLE_ADMIN));
    }
}
