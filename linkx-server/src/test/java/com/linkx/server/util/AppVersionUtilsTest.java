package com.linkx.server.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("应用版本工具测试")
class AppVersionUtilsTest {

    @Test
    @DisplayName("版本数字段比较")
    void compareNumeric() {
        assertTrue(AppVersionUtils.compare("1.9.0", "1.10.0") < 0);
        assertTrue(AppVersionUtils.compare("1.10.0", "1.9.0") > 0);
        assertEquals(0, AppVersionUtils.compare("1.0.0", "1.0.0"));
    }

    @Test
    @DisplayName("渠道可见性：stable 全员可见，灰度仅同渠道")
    void channelEligible() {
        assertTrue(AppVersionUtils.isChannelEligible(null, "beta"));
        assertTrue(AppVersionUtils.isChannelEligible("", "beta"));
        assertTrue(AppVersionUtils.isChannelEligible("stable", "stable"));
        assertTrue(AppVersionUtils.isChannelEligible("beta", "stable"));
        assertTrue(AppVersionUtils.isChannelEligible("beta", "beta"));
        assertFalse(AppVersionUtils.isChannelEligible("stable", "beta"));
        assertFalse(AppVersionUtils.isChannelEligible("stable", "dev"));
        assertTrue(AppVersionUtils.isChannelEligible("dev", "dev"));
    }
}
