package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * apiSignTtlSeconds 硬上限测试：
 * 签名时间窗口被硬性限制在 [30, 180]s，保证窗口 ≤ nonce 去重 TTL，闭合防重放空隙。
 */
class LinkxPropertiesApiSignTtlHardCapTest {

    private final LinkxProperties.Security security = new LinkxProperties.Security();

    @Test
    void clamps_to_upper_bound_when_exceeding() {
        security.setApiSignTtlSeconds(600);
        assertEquals(180, security.getApiSignTtlSeconds());
    }

    @Test
    void clamps_to_lower_bound_when_below() {
        security.setApiSignTtlSeconds(10);
        assertEquals(30, security.getApiSignTtlSeconds());

        security.setApiSignTtlSeconds(0);
        assertEquals(30, security.getApiSignTtlSeconds());
    }

    @Test
    void preserves_value_within_bounds() {
        security.setApiSignTtlSeconds(30);
        assertEquals(30, security.getApiSignTtlSeconds());

        security.setApiSignTtlSeconds(120);
        assertEquals(120, security.getApiSignTtlSeconds());

        security.setApiSignTtlSeconds(180);
        assertEquals(180, security.getApiSignTtlSeconds());
    }
}