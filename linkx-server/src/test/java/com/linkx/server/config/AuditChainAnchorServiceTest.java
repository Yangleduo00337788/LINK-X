package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 审计链尾外部锚定：行解析与异常输入处理。
 */
class AuditChainAnchorServiceTest {

    private static final String HASH = "3fa7abcd".repeat(8);

    @Test
    void parseLine_happyPath() {
        AuditChainAnchorService.AnchorPoint point =
                AuditChainAnchorService.parseLine("1756842000000 1042 " + HASH);
        assertNotNull(point);
        assertEquals(1042L, point.id());
        assertEquals(HASH, point.hash());
    }

    @Test
    void parseLine_toleratesLeadingSpaces() {
        AuditChainAnchorService.AnchorPoint point =
                AuditChainAnchorService.parseLine("  1756842000000  1042  " + HASH);
        assertNotNull(point);
        assertEquals(1042L, point.id());
        assertEquals(HASH, point.hash());
    }

    @Test
    void parseLine_nullOrBadInput_returnsNull() {
        assertNull(AuditChainAnchorService.parseLine(null));
        assertNull(AuditChainAnchorService.parseLine(""));
        assertNull(AuditChainAnchorService.parseLine("short"));
        assertNull(AuditChainAnchorService.parseLine("1 notanid abc"));
        assertNull(AuditChainAnchorService.parseLine("1 2")); // 缺哈希段
    }
}