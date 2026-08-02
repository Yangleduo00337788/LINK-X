package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.RbacConstants;
import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysRolePermission;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysPermissionMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.SysRolePermissionMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.AuditLogService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RbacServiceImpl RBAC")
class RbacServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long ROLE_ID = 100L;
    private static final Long PERM_ID = 200L;

    @Mock SysRoleMapper sysRoleMapper;
    @Mock SysUserRoleMapper sysUserRoleMapper;
    @Mock SysPermissionMapper sysPermissionMapper;
    @Mock SysRolePermissionMapper sysRolePermissionMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuditLogService auditLogService;

    private ObjectMapper objectMapper;
    private RbacServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RbacServiceImpl(
                sysRoleMapper, sysUserRoleMapper, sysPermissionMapper,
                sysRolePermissionMapper, redisTemplate, objectMapper, auditLogService
        );
    }

    private SysUserRole userRole(long roleId) {
        return SysUserRole.builder().userId(USER_ID).roleId(roleId).build();
    }

    private SysRole role(long id, String code) {
        return SysRole.builder().id(id).roleCode(code).roleName(code).status(1).build();
    }

    private SysPermission permission(long id, String code) {
        return SysPermission.builder().id(id).permissionCode(code).status(1).build();
    }

    @Nested
    @DisplayName("查询")
    class Query {
        @Test
        @DisplayName("getUserRoles / getUserPermissions 空 userId")
        void nullUserId() {
            assertTrue(service.getUserRoles(null).isEmpty());
            assertTrue(service.getUserPermissions(null).isEmpty());
            assertTrue(service.getUserRoleCodes(null).isEmpty());
            assertTrue(service.getUserPermissionCodes(null).isEmpty());
        }

        @Test
        @DisplayName("getUserRoles 无角色")
        void noRoles() {
            when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            assertTrue(service.getUserRoles(USER_ID).isEmpty());
        }

        @Test
        @DisplayName("getUserRoles / getUserPermissions 成功")
        void success() {
            when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(userRole(ROLE_ID)));
            when(sysRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(role(ROLE_ID, "editor")));
            when(sysRolePermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(SysRolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build()));
            when(sysPermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(permission(PERM_ID, "user:read")));

            assertEquals(1, service.getUserRoles(USER_ID).size());
            assertEquals("user:read", service.getUserPermissions(USER_ID).get(0).getPermissionCode());
        }

        @Test
        @DisplayName("getUserRoleCodes 读缓存")
        void roleCodesFromCache() throws Exception {
            List<String> cached = List.of("editor");
            when(valueOps.get(RbacConstants.CACHE_KEY_ROLES + USER_ID))
                    .thenReturn(objectMapper.writeValueAsString(cached));
            assertEquals(cached, service.getUserRoleCodes(USER_ID));
            verify(sysUserRoleMapper, never()).selectListByQuery(any());
        }
    }

    @Nested
    @DisplayName("hasPermission / hasRole")
    class Checks {
        @Test
        @DisplayName("hasPermission 精确匹配与通配符")
        void hasPermission() {
            when(valueOps.get(RbacConstants.CACHE_KEY_PERMS + USER_ID)).thenReturn(null);
            when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(userRole(ROLE_ID)));
            when(sysRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(role(ROLE_ID, RbacConstants.ROLE_ADMIN)));
            when(sysRolePermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(SysRolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build()));
            when(sysPermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(permission(PERM_ID, RbacConstants.PERM_ALL)));

            assertTrue(service.hasPermission(USER_ID, "any:perm"));
            assertFalse(service.hasPermission(USER_ID, null));
            assertFalse(service.hasPermission(null, "user:read"));
        }

        @Test
        @DisplayName("hasAnyPermission 等价于多次 hasPermission")
        void hasAnyPermission() {
            when(valueOps.get(RbacConstants.CACHE_KEY_PERMS + USER_ID)).thenReturn(null);
            when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(userRole(ROLE_ID)));
            when(sysRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(role(ROLE_ID, "editor")));
            when(sysRolePermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(SysRolePermission.builder().roleId(ROLE_ID).permissionId(PERM_ID).build()));
            when(sysPermissionMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(permission(PERM_ID, "user:read")));

            assertTrue(service.hasPermission(USER_ID, "user:read"));
            assertFalse(service.hasPermission(USER_ID, "user:delete"));
        }

        @Test
        @DisplayName("isSuperAdmin 即 hasRole(super_admin)")
        void isSuperAdmin() {
            when(valueOps.get(RbacConstants.CACHE_KEY_ROLES + USER_ID)).thenReturn(null);
            when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(userRole(ROLE_ID)));
            when(sysRoleMapper.selectListByQuery(any(QueryWrapper.class)))
                    .thenReturn(List.of(role(ROLE_ID, RbacConstants.ROLE_SUPER_ADMIN)));

            assertTrue(service.hasRole(USER_ID, RbacConstants.ROLE_SUPER_ADMIN));
            assertFalse(service.hasRole(USER_ID, RbacConstants.ROLE_ADMIN));
            assertFalse(service.hasRole(USER_ID, null));
        }
    }

    @Nested
    @DisplayName("grantRole / revokeRole")
    class Assign {
        @Test
        @DisplayName("grantRole 参数为空")
        void grantNullParams() {
            assertThrows(CustomException.class, () -> service.grantRole(null, "editor", 1L));
            assertThrows(CustomException.class, () -> service.grantRole(USER_ID, null, 1L));
        }

        @Test
        @DisplayName("grantRole 角色不存在")
        void grantRoleMissing() {
            when(sysRoleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.grantRole(USER_ID, "missing", 1L));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("grantRoleById 禁止授予管理员")
        void grantAdminForbidden() {
            when(sysRoleMapper.selectOneById(ROLE_ID))
                    .thenReturn(role(ROLE_ID, RbacConstants.ROLE_ADMIN));
            CustomException ex = assertThrows(CustomException.class,
                    () -> service.grantRoleById(USER_ID, ROLE_ID, 1L));
            assertEquals(403, ex.getCode());

            when(sysRoleMapper.selectOneById(ROLE_ID))
                    .thenReturn(role(ROLE_ID, RbacConstants.ROLE_SUPER_ADMIN));
            assertThrows(CustomException.class, () -> service.grantRoleById(USER_ID, ROLE_ID, 1L));
        }

        @Test
        @DisplayName("grantRole 成功并清缓存")
        void grantSuccess() {
            SysRole editor = role(ROLE_ID, "editor");
            when(sysRoleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editor);
            when(sysRoleMapper.selectOneById(ROLE_ID)).thenReturn(editor);

            service.grantRole(USER_ID, "editor", 1L);

            verify(sysUserRoleMapper).insert(any(SysUserRole.class));
            verify(auditLogService).log(any(), contains("editor"), eq(1L),
                    isNull(), isNull(), isNull(), eq(true), isNull());
            verify(redisTemplate).delete(RbacConstants.CACHE_KEY_ROLES + USER_ID);
            verify(redisTemplate).delete(RbacConstants.CACHE_KEY_PERMS + USER_ID);
        }

        @Test
        @DisplayName("grantRole 幂等重复键")
        void grantDuplicate() {
            SysRole editor = role(ROLE_ID, "editor");
            when(sysRoleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editor);
            when(sysRoleMapper.selectOneById(ROLE_ID)).thenReturn(editor);
            doThrow(new DuplicateKeyException("dup")).when(sysUserRoleMapper).insert(any(SysUserRole.class));

            assertDoesNotThrow(() -> service.grantRole(USER_ID, "editor", 1L));
            verify(auditLogService, never()).log(any(), anyString(), any(), any(), any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("revokeRole 角色不存在静默返回")
        void revokeMissingRole() {
            when(sysRoleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            service.revokeRole(USER_ID, "ghost");
            verify(sysUserRoleMapper, never()).deleteByQuery(any());
        }

        @Test
        @DisplayName("revokeRoleById 禁止撤销管理员")
        void revokeAdminForbidden() {
            when(sysRoleMapper.selectOneById(ROLE_ID))
                    .thenReturn(role(ROLE_ID, RbacConstants.ROLE_ADMIN));
            assertThrows(CustomException.class, () -> service.revokeRoleById(USER_ID, ROLE_ID));
        }

        @Test
        @DisplayName("revokeRole 成功")
        void revokeSuccess() {
            SysRole editor = role(ROLE_ID, "editor");
            when(sysRoleMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(editor);
            when(sysRoleMapper.selectOneById(ROLE_ID)).thenReturn(editor);
            when(sysUserRoleMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

            service.revokeRole(USER_ID, "editor");

            verify(auditLogService).log(any(), contains("撤销角色"), isNull(),
                    isNull(), isNull(), isNull(), eq(true), isNull());
            verify(redisTemplate).delete(RbacConstants.CACHE_KEY_ROLES + USER_ID);
        }

        @Test
        @DisplayName("revokeRole null 参数直接返回")
        void revokeNullParams() {
            service.revokeRole(null, "editor");
            service.revokeRole(USER_ID, null);
            verifyNoInteractions(sysRoleMapper);
        }
    }
}
