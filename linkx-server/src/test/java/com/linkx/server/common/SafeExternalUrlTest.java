package com.linkx.server.common;

import com.linkx.server.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafeExternalUrl 测试")
class SafeExternalUrlTest {

    @Test
    void formatIpLiteral_ipv4() throws Exception {
        InetAddress ip = InetAddress.getByName("8.8.8.8");
        assertEquals("8.8.8.8", SafeExternalUrl.formatIpLiteral(ip));
    }

    @Test
    void formatIpLiteral_ipv6_withBrackets() throws Exception {
        InetAddress ip = InetAddress.getByName("2001:4860:4860::8888");
        String literal = SafeExternalUrl.formatIpLiteral(ip);
        assertTrue(literal.startsWith("["));
        assertTrue(literal.endsWith("]"));
        assertFalse(literal.contains("%"));
    }

    @Test
    void parseAndValidate_rejectsPrivate() {
        CustomException ex = assertThrows(CustomException.class,
                () -> SafeExternalUrl.parseAndValidate("http://127.0.0.1/a.png"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void parseAndValidate_rejectsNonHttp() {
        assertThrows(CustomException.class,
                () -> SafeExternalUrl.parseAndValidate("ftp://example.com/a.png"));
    }

    @Test
    void parseAndValidate_acceptsPublicHttp() {
        // 使用 example.com（文档保留域名，解析为公网）
        assertDoesNotThrow(() -> SafeExternalUrl.parseAndValidate("https://example.com/img.png"));
    }

    @Test
    void allowRestrictedHeaders_enabled() throws Exception {
        Class.forName("com.linkx.server.common.SafeExternalUrl");
        assertEquals("true", System.getProperty("sun.net.http.allowRestrictedHeaders"));
    }
}
