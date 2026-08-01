package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.common.admin.DataScopeType;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysRoleDept;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysRoleDeptMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端数据权限")
class AdminDataScopeIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final long ROLE_OPS = 1003L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysRoleDeptMapper sysRoleDeptMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("部门树：超管可查询")
    void deptTreeVisibleToAdmin() throws Exception {
        TestUser admin = registerAndLogin("dsadm");
        grantRole(admin.userId, ADMIN_ROLE);
        admin = login(admin.username, "Test1234abcd");

        mockMvc.perform(get("/admin/depts").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("总部"));
    }

    @Test
    @DisplayName("角色 dataScope=SELF 时用户列表仅见本人")
    void selfScopeLimitsUserList() throws Exception {
        Integer original = sysRoleMapper.selectOneById(ROLE_OPS).getDataScope();
        try {
            TestUser ops = registerAndLogin("dsself");
            grantRole(ops.userId, ROLE_OPS);
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, DataScopeType.SELF)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
            rbacService.evictUserCache(ops.userId);
            ops = login(ops.username, "Test1234abcd");

            TestUser other = registerAndLogin("dsoth");

            MvcResult result = mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "50")
                            .header("Authorization", ops.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            Set<Long> ids = userIdsFromPage(result);
            assertTrue(ids.contains(ops.userId));
            assertFalse(ids.contains(other.userId));
        } finally {
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, original == null ? DataScopeType.ALL : original)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
        }
    }

    @Test
    @DisplayName("角色 dataScope=DEPT 时可见同部门用户")
    void deptScopeSeesSameDeptUsers() throws Exception {
        Integer original = sysRoleMapper.selectOneById(ROLE_OPS).getDataScope();
        try {
            TestUser ops = registerAndLogin("dsdept");
            grantRole(ops.userId, ROLE_OPS);
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, 2L)
                    .where(SysUser::getId).eq(ops.userId)
                    .update();
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, DataScopeType.DEPT)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
            rbacService.evictUserCache(ops.userId);
            ops = login(ops.username, "Test1234abcd");

            TestUser sameDept = registerAndLogin("dssame");
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, 2L)
                    .where(SysUser::getId).eq(sameDept.userId)
                    .update();

            TestUser otherDept = registerAndLogin("dsdiff");
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, 3L)
                    .where(SysUser::getId).eq(otherDept.userId)
                    .update();

            MvcResult result = mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "100")
                            .header("Authorization", ops.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            Set<Long> ids = userIdsFromPage(result);
            assertTrue(ids.contains(ops.userId));
            assertTrue(ids.contains(sameDept.userId));
            assertFalse(ids.contains(otherDept.userId));
        } finally {
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, original == null ? DataScopeType.ALL : original)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
        }
    }

    @Test
    @DisplayName("角色 dataScope=CUSTOM 时仅见绑定部门用户")
    void customScopeSeesBoundDeptUsers() throws Exception {
        Integer original = sysRoleMapper.selectOneById(ROLE_OPS).getDataScope();
        try {
            TestUser ops = registerAndLogin("dscust");
            grantRole(ops.userId, ROLE_OPS);
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, DataScopeType.CUSTOM)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
            sysRoleDeptMapper.deleteByQuery(
                    QueryWrapper.create().where(SysRoleDept::getRoleId).eq(ROLE_OPS));
            sysRoleDeptMapper.insert(SysRoleDept.builder()
                    .roleId(ROLE_OPS)
                    .deptId(3L)
                    .createTime(new Date())
                    .build());
            rbacService.evictUserCache(ops.userId);
            ops = login(ops.username, "Test1234abcd");

            TestUser inScope = registerAndLogin("dscin");
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, 3L)
                    .where(SysUser::getId).eq(inScope.userId)
                    .update();

            TestUser outScope = registerAndLogin("dscout");
            UpdateChain.of(SysUser.class)
                    .set(SysUser::getDeptId, 2L)
                    .where(SysUser::getId).eq(outScope.userId)
                    .update();

            MvcResult result = mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "100")
                            .header("Authorization", ops.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            Set<Long> ids = userIdsFromPage(result);
            assertTrue(ids.contains(ops.userId));
            assertTrue(ids.contains(inScope.userId));
            assertFalse(ids.contains(outScope.userId));
        } finally {
            sysRoleDeptMapper.deleteByQuery(
                    QueryWrapper.create().where(SysRoleDept::getRoleId).eq(ROLE_OPS));
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, original == null ? DataScopeType.ALL : original)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
        }
    }

    @Test
    @DisplayName("角色可更新 dataScope 字段")
    void updateRoleDataScope() throws Exception {
        Integer original = sysRoleMapper.selectOneById(ROLE_OPS).getDataScope();
        try {
            TestUser admin = registerAndLogin("dsrole");
            grantRole(admin.userId, ADMIN_ROLE);
            admin = login(admin.username, "Test1234abcd");

            mockMvc.perform(put("/admin/roles/{id}", ROLE_OPS)
                            .header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleCode\":\"ops_admin\",\"roleName\":\"运营管理员\",\"dataScope\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            SysRole role = sysRoleMapper.selectOneById(ROLE_OPS);
            assertEquals(DataScopeType.DEPT, role.getDataScope());
        } finally {
            UpdateChain.of(SysRole.class)
                    .set(SysRole::getDataScope, original == null ? DataScopeType.ALL : original)
                    .where(SysRole::getId).eq(ROLE_OPS)
                    .update();
        }
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

    private Set<Long> userIdsFromPage(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Set<Long> ids = new HashSet<>();
        for (JsonNode item : root.path("data").path("items")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }
}
