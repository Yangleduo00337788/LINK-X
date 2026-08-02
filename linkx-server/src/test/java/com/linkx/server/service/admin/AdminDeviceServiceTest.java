package com.linkx.server.service.admin;

import com.linkx.server.common.DataScopeContext;
import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.SysDeviceBan;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.SysDeviceBanMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.PresenceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.impl.AdminDeviceServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDeviceService 设备管理")
class AdminDeviceServiceTest {

    @Mock
    private DeviceSessionMapper deviceSessionMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysDeviceBanMapper deviceBanMapper;
    @Mock
    private DeviceSessionService deviceSessionService;
    @Mock
    private PresenceService presenceService;
    @Mock
    private AdminEventPublisher adminEventPublisher;
    @Mock
    private AuditLogService auditLogService;

    private AdminDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        DataScopeContext.setUnrestricted();
        service = new AdminDeviceServiceImpl(
                deviceSessionMapper, sysUserMapper, deviceBanMapper, deviceSessionService,
                presenceService, adminEventPublisher, auditLogService);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("列表应填充用户名并分页")
    void list_fillsUsername() {
        DeviceSession session = DeviceSession.builder()
                .id(1L)
                .userId(100L)
                .deviceId("dev-1")
                .deviceName("Chrome")
                .deviceType("Web")
                .ip("1.2.3.4")
                .lastActive(new Date())
                .createTime(new Date())
                .build();
        when(deviceSessionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(deviceSessionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(session));
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUser.builder().id(100L).username("alice").nickname("Alice").build()
        ));
        when(presenceService.onlineDeviceIds(100L)).thenReturn(Set.of("dev-1"));
        when(deviceBanMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AdminDeviceQueryDTO query = new AdminDeviceQueryDTO();
        query.setPage(1);
        query.setSize(20);
        var result = service.list(query);

        assertEquals(1, result.getTotal());
        AdminDeviceVO item = result.getItems().get(0);
        assertEquals("alice", item.getUsername());
        assertEquals("dev-1", item.getDeviceId());
        assertEquals(Boolean.TRUE, item.getOnline());
    }

    @Test
    @DisplayName("踢下线应校验会话存在并委托 DeviceSessionService")
    void kick_delegatesWhenSessionExists() {
        when(deviceSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                DeviceSession.builder().userId(100L).deviceId("dev-1").build()
        );
        when(sysUserMapper.selectOneById(9L)).thenReturn(
                SysUser.builder().id(9L).username("admin").build()
        );

        service.kick(100L, "dev-1", 9L, null, "127.0.0.1", "JUnit");

        ArgumentCaptor<String> operatorCaptor = ArgumentCaptor.forClass(String.class);
        verify(deviceSessionService).kickDevice(
                eq(100L), eq("dev-1"), eq(9L), operatorCaptor.capture(), eq("127.0.0.1"), eq("JUnit"));
        assertEquals("admin", operatorCaptor.getValue());
    }

    @Test
    @DisplayName("踢下线会话不存在时应 404")
    void kick_throwsWhenMissing() {
        when(deviceSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.kick(100L, "missing", 9L, null, "127.0.0.1", "JUnit"));
        assertEquals(404, ex.getCode());
        verify(deviceSessionService, never()).kickDevice(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("导出列表与关键字筛选")
    void listForExport_and_keyword() {
        DeviceSession session = DeviceSession.builder()
                .id(2L).userId(200L).deviceId("dev-2").deviceName("Phone")
                .deviceType("Mobile").ip("9.9.9.9").lastActive(new Date()).createTime(new Date())
                .build();
        when(deviceSessionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(session));
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUser.builder().id(200L).username("bob").nickname("Bob").build()
        ));
        when(presenceService.onlineDeviceIds(200L)).thenReturn(Set.of());
        when(deviceBanMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AdminDeviceQueryDTO query = new AdminDeviceQueryDTO();
        query.setKeyword("bob");
        query.setDeviceType("Mobile");
        query.setStartTime(1L);
        query.setEndTime(System.currentTimeMillis());
        assertEquals(1, service.listForExport(query).size());
    }

    @Test
    @DisplayName("封禁设备并踢下线")
    void ban_device() {
        when(sysUserMapper.selectOneById(100L)).thenReturn(
                SysUser.builder().id(100L).username("alice").build());
        when(deviceBanMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(deviceSessionMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(
                DeviceSession.builder().userId(100L).deviceId("dev-1")
                        .deviceName("Chrome").deviceType("Web").ip("1.1.1.1").userAgent("UA").build()
        );
        when(sysUserMapper.selectOneById(9L)).thenReturn(
                SysUser.builder().id(9L).username("admin").build());

        service.ban(100L, "dev-1", "违规", 9L, "127.0.0.1", "JUnit");

        verify(deviceBanMapper).insert(any(SysDeviceBan.class));
        verify(deviceSessionService).kickDevice(eq(100L), eq("dev-1"), eq(9L), eq("admin"),
                eq("127.0.0.1"), eq("JUnit"));
        verify(deviceSessionService).createOrUpdate(eq(100L), eq("dev-1"), eq("Chrome"),
                eq("Web"), eq("1.1.1.1"), eq("UA"));
        verify(auditLogService).logWithTarget(any(), anyString(), eq(9L), eq("admin"),
                eq(100L), eq("alice"), eq("dev-1"), eq("device"), eq("127.0.0.1"), eq("JUnit"),
                eq(true), isNull());
    }

    @Test
    @DisplayName("封禁守卫：用户不存在/已封禁")
    void ban_guards() {
        when(sysUserMapper.selectOneById(100L)).thenReturn(null);
        assertThrows(CustomException.class,
                () -> service.ban(100L, "dev-1", null, 9L, "127.0.0.1", "JUnit"));

        when(sysUserMapper.selectOneById(100L)).thenReturn(
                SysUser.builder().id(100L).username("alice").build());
        when(deviceBanMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        assertThrows(CustomException.class,
                () -> service.ban(100L, "dev-1", "r", 9L, "127.0.0.1", "JUnit"));
    }

    @Test
    @DisplayName("解封设备")
    void unban_device() {
        SysDeviceBan ban = SysDeviceBan.builder()
                .id(1L).userId(100L).deviceId("dev-1").status(SysDeviceBan.STATUS_ACTIVE)
                .createTime(new Date()).updateTime(new Date()).build();
        when(deviceBanMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(ban);
        when(sysUserMapper.selectOneById(100L)).thenReturn(
                SysUser.builder().id(100L).username("alice").build());
        when(sysUserMapper.selectOneById(9L)).thenReturn(
                SysUser.builder().id(9L).username("admin").build());

        service.unban(100L, "dev-1", 9L, "127.0.0.1", "JUnit");

        assertEquals(SysDeviceBan.STATUS_RELEASED, ban.getStatus());
        verify(deviceBanMapper).update(ban);
        verify(auditLogService).logWithTarget(any(), contains("解封"), eq(9L), eq("admin"),
                eq(100L), eq("alice"), eq("dev-1"), eq("device"), anyString(), anyString(),
                eq(true), isNull());
    }

    @Test
    @DisplayName("解封无记录时 404")
    void unban_notFound() {
        when(deviceBanMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        assertThrows(CustomException.class,
                () -> service.unban(100L, "missing", 9L, "127.0.0.1", "JUnit"));
    }
}
