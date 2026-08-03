package com.linkx.server.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("IP 归属地解析")
class IpGeoServiceImplTest {

    @Test
    @DisplayName("空 IP 返回 null")
    void resolveEmptyIp() {
        IpGeoServiceImpl service = new IpGeoServiceImpl(mock(com.linkx.server.config.LinkxProperties.class));
        assertEquals(null, service.resolve(null));
        assertEquals(null, service.resolve(""));
        assertEquals(null, service.resolve("   "));
    }

    @Test
    @DisplayName("内网 / 回环地址识别")
    void privateIps() {
        assertTrue(IpGeoServiceImpl.isPrivateOrLocal("127.0.0.1"));
        assertTrue(IpGeoServiceImpl.isPrivateOrLocal("192.168.1.1"));
        assertTrue(IpGeoServiceImpl.isPrivateOrLocal("10.0.0.8"));
        assertTrue(IpGeoServiceImpl.isPrivateOrLocal("::1"));
    }

    @Test
    @DisplayName("region 原始串格式化")
    void formatRegion() {
        assertEquals("中国 广东省 深圳市 电信",
                IpGeoServiceImpl.formatRegion("中国|0|广东省|深圳市|电信"));
        assertEquals("未知", IpGeoServiceImpl.formatRegion("0|0|0|0|0"));
        assertEquals("未知", IpGeoServiceImpl.formatRegion(""));
    }
}
