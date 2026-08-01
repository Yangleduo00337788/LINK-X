package com.linkx.server.service;

import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeviceBanMapper;
import com.linkx.server.mapper.SysUserDeviceBindingMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.impl.DeviceSecurityServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceSecurityService 设备封禁与强绑定")
class DeviceSecurityServiceTest {

    @Mock
    private SysDeviceBanMapper deviceBanMapper;
    @Mock
    private SysUserDeviceBindingMapper deviceBindingMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    private DeviceSecurityService service;

    @BeforeEach
    void setUp() {
        service = new DeviceSecurityServiceImpl(deviceBanMapper, deviceBindingMapper, sysUserMapper);
    }

    @Test
    @DisplayName("封禁设备应拒绝登录")
    void bannedDevice_rejected() {
        when(deviceBanMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        CustomException ex = assertThrows(CustomException.class,
                () -> service.assertDeviceAllowed(10L, "dev-a"));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("封禁"));
    }

    @Test
    @DisplayName("强绑定未批准应拒绝登录")
    void bindingEnabled_unapproved_rejected() {
        when(deviceBanMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sysUserMapper.selectOneById(10L)).thenReturn(
                SysUser.builder().id(10L).deviceBindingEnabled(1).build());
        when(deviceBindingMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        CustomException ex = assertThrows(CustomException.class,
                () -> service.assertDeviceAllowed(10L, "dev-b"));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("强绑定"));
    }

    @Test
    @DisplayName("强绑定已批准应放行")
    void bindingEnabled_approved_ok() {
        when(deviceBanMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sysUserMapper.selectOneById(10L)).thenReturn(
                SysUser.builder().id(10L).deviceBindingEnabled(1).build());
        when(deviceBindingMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        assertDoesNotThrow(() -> service.assertDeviceAllowed(10L, "dev-c"));
    }
}
