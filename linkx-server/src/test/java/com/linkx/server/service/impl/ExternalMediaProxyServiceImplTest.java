package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.ExternalMediaProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExternalMediaProxyServiceImpl 单元测试")
class ExternalMediaProxyServiceImplTest {

    private ExternalMediaProxyServiceImpl service;

    @BeforeEach
    void setUp() {
        LinkxProperties props = new LinkxProperties();
        // jwt secret used for HMAC signing
        props.getJwt().setSecret("Test-Local-JWT-Key-For-Integration-2026!!");
        service = new ExternalMediaProxyServiceImpl(props);
    }

    @Test
    @DisplayName("空 URL 原样返回")
    void wrap_blank_passthrough() {
        assertNull(service.wrapExternalUrl(null));
        assertEquals("", service.wrapExternalUrl(""));
        assertEquals("  ", service.wrapExternalUrl("  "));
    }

    @Test
    @DisplayName("公网 HTTPS 应包装为 /media/external")
    void wrap_publicHttps() {
        String wrapped = service.wrapExternalUrl("https://example.com/a.png");
        assertTrue(wrapped.startsWith("/media/external?"));
        assertTrue(wrapped.contains("u="));
        assertTrue(wrapped.contains("&e="));
        assertTrue(wrapped.contains("&s="));
    }

    @Test
    @DisplayName("内网地址包装应失败")
    void wrap_private_fails() {
        assertThrows(CustomException.class, () -> service.wrapExternalUrl("http://127.0.0.1/x.png"));
    }

    @Test
    @DisplayName("fetch 过期窗口过大应 403")
    void fetch_expTooFar() {
        long exp = Instant.now().getEpochSecond() + 7 * 3600L + 120;
        CustomException ex = assertThrows(CustomException.class,
                () -> service.fetch("https://example.com/a.png", exp, "deadbeef"));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("HTTP 公网 URL 可包装")
    void wrap_httpPublic() {
        String wrapped = service.wrapExternalUrl("http://example.com/a.png");
        assertTrue(wrapped.startsWith("/media/external?"));
    }

    @Test
    @DisplayName("JWT secret 缺失时签名失败")
    void signWithoutSecret() {
        LinkxProperties props = new LinkxProperties();
        props.getJwt().setSecret("");
        ExternalMediaProxyServiceImpl noSecret = new ExternalMediaProxyServiceImpl(props);
        assertThrows(IllegalStateException.class,
                () -> noSecret.wrapExternalUrl("https://example.com/a.png"));
    }

    @Test
    @DisplayName("fetch 缺参应 400")
    void fetch_missingParams() {
        CustomException ex = assertThrows(CustomException.class, () -> service.fetch("", 0, ""));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("fetch 过期应 403")
    void fetch_expired() {
        CustomException ex = assertThrows(CustomException.class,
                () -> service.fetch("https://example.com/a.png", 1L, "deadbeef"));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("fetch 签名无效应 403")
    void fetch_badSig() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        CustomException ex = assertThrows(CustomException.class,
                () -> service.fetch("https://example.com/a.png", exp, "00"));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("wrap 后再 fetch 签名应通过校验（拉取可能因网络失败）")
    void wrapThenFetch_sigOkOrNetwork() {
        String wrapped = service.wrapExternalUrl("https://example.com/a.png");
        // /media/external?u=...&e=...&s=...
        String query = wrapped.substring(wrapped.indexOf('?') + 1);
        String u = null;
        long e = 0;
        String s = null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length < 2) continue;
            switch (kv[0]) {
                case "u" -> u = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                case "e" -> e = Long.parseLong(kv[1]);
                case "s" -> s = kv[1];
            }
        }
        assertNotNull(u);
        assertNotNull(s);
        try {
            ExternalMediaProxyService.ProxiedImage img = service.fetch(u, e, s);
            assertNotNull(img);
        } catch (CustomException ex) {
            // 网络/对端不可用时允许 502，但不允许签名类 403
            assertNotEquals(403, ex.getCode());
            assertTrue(ex.getCode() == 502 || ex.getCode() == 400 || ex.getCode() >= 500);
        }
    }
}
