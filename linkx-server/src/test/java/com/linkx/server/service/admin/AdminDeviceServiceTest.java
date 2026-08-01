package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;
import com.linkx.server.entity.DeviceSession;
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
        service = new AdminDeviceServiceImpl(
                deviceSessionMapper, sysUserMapper, deviceBanMapper, deviceSessionService,
                presenceService, adminEventPublisher, auditLogService);
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
}
