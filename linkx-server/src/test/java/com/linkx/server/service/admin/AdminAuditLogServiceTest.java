package com.linkx.server.service.admin;

import com.linkx.server.common.DataScopeContext;
import com.linkx.server.controller.admin.dto.AdminAuditLogQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.admin.impl.AdminAuditLogServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuditLogService 审计日志")
class AdminAuditLogServiceTest {

    @Mock SysAuditLogMapper sysAuditLogMapper;
    @Mock SysLoginAuditMapper sysLoginAuditMapper;
    @Mock IpGeoService ipGeoService;
    @Mock com.linkx.server.mapper.SysUserMapper sysUserMapper;

    private AdminAuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        DataScopeContext.setUnrestricted();
        service = new AdminAuditLogServiceImpl(sysAuditLogMapper, sysLoginAuditMapper, ipGeoService, sysUserMapper);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private SysAuditLog auditLog(Long id, Long userId) {
        return SysAuditLog.builder()
                .id(id)
                .userId(userId)
                .username("admin" + userId)
                .operationType("user_update")
                .description("更新用户")
                .status("success")
                .ip("127.0.0.1")
                .userAgent("JUnit")
                .createTime(new Date())
                .build();
    }

    private SysLoginAudit loginLog(Long id, Long userId) {
        return SysLoginAudit.builder()
                .id(id)
                .userId(userId)
                .username("user" + userId)
                .ip("8.8.8.8")
                .success(1)
                .reason(null)
                .userAgent("Chrome")
                .createTime(new Date())
                .build();
    }

    @Test
    @DisplayName("操作日志列表/导出/详情")
    void audit_list_export_detail() {
        when(sysAuditLogMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(sysAuditLogMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(auditLog(1L, 10L)));

        AdminAuditLogQueryDTO q = new AdminAuditLogQueryDTO();
        q.setPage(1);
        q.setSize(20);
        q.setKeyword("admin");
        q.setOperationType("user_update");
        q.setResultStatus("success");
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());

        assertEquals(1, service.listAuditLogs(q).getTotal());
        assertEquals(1, service.listAuditLogsForExport(q).size());

        when(sysAuditLogMapper.selectOneById(1L)).thenReturn(auditLog(1L, 10L));
        assertEquals(1L, service.auditDetail(1L).getId());

        when(sysAuditLogMapper.selectOneById(99L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.auditDetail(99L));
    }

    @Test
    @DisplayName("登录日志列表/导出/详情")
    void login_list_export_detail() {
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(sysLoginAuditMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(loginLog(2L, 20L)));
        when(ipGeoService.resolve("8.8.8.8")).thenReturn("US");

        AdminPageQueryDTO q = new AdminPageQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setKeyword("user");
        q.setStatus(1);
        q.setStartTime(1L);
        q.setEndTime(System.currentTimeMillis());

        assertEquals(1, service.listLoginLogs(q).getTotal());
        assertEquals("US", service.listLoginLogs(q).getItems().get(0).getRegion());
        assertEquals(1, service.listLoginLogsForExport(q).size());

        when(sysLoginAuditMapper.selectOneById(2L)).thenReturn(loginLog(2L, 20L));
        assertEquals(2L, service.loginDetail(2L).getId());
    }

    @Test
    @DisplayName("数据权限：范围外详情不可见")
    void dataScope_blocksOutOfScopeDetail() {
        DataScopeContext.setAllowedUserIds(Set.of(10L));
        when(sysAuditLogMapper.selectOneById(5L)).thenReturn(auditLog(5L, 99L));
        assertThrows(CustomException.class, () -> service.auditDetail(5L));

        when(sysLoginAuditMapper.selectOneById(6L)).thenReturn(loginLog(6L, 88L));
        assertThrows(CustomException.class, () -> service.loginDetail(6L));
    }

    @Test
    @DisplayName("数据权限：空集合列表仍可调通")
    void dataScope_emptyAllowed() {
        DataScopeContext.setAllowedUserIds(Set.of());
        when(sysAuditLogMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sysAuditLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(sysLoginAuditMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sysLoginAuditMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AdminPageQueryDTO q = new AdminPageQueryDTO();
        q.setPage(1);
        q.setSize(10);
        assertEquals(0, service.listAuditLogs(new AdminAuditLogQueryDTO()).getTotal());
        assertEquals(0, service.listLoginLogs(q).getTotal());
    }
}
