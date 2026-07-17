package com.linkx.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LinkxProperties 配置类测试
 */
@DisplayName("LinkxProperties 配置类测试")
class LinkxPropertiesTest {

    @Nested
    @DisplayName("默认配置值测试")
    class DefaultValuesTests {

        @Test
        @DisplayName("默认JWT配置应正确")
        void defaultJwtConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getJwt());
            assertEquals(1_800_000L, props.getJwt().getAccessExpire()); // 30分钟
            assertEquals(259_200_000L, props.getJwt().getRefreshExpire()); // 3天
        }

        @Test
        @DisplayName("默认Auth配置应正确")
        void defaultAuthConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getAuth());
            assertTrue(props.getAuth().isCaptchaEnabled());
            assertEquals(5, props.getAuth().getLoginMaxAttempts());
            assertEquals(15, props.getAuth().getLockDurationMinutes());
        }

        @Test
        @DisplayName("默认Minio配置应正确")
        void defaultMinioConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getMinio());
            assertEquals("http://localhost:9000", props.getMinio().getEndpoint());
            assertEquals("minioadmin", props.getMinio().getAccessKey());
            assertEquals("minioadmin123", props.getMinio().getSecretKey());
            assertEquals("linkx", props.getMinio().getBucketName());
            assertEquals(10 * 1024 * 1024, props.getMinio().getMaxFileSize());
        }

        @Test
        @DisplayName("默认Im配置应正确")
        void defaultImConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getIm());
            assertEquals(8081, props.getIm().getWebsocketPort());
            assertEquals("/ws", props.getIm().getWebsocketPath());
            assertEquals(30, props.getIm().getHeartbeatIntervalSeconds());
        }

        @Test
        @DisplayName("默认Proxy配置应正确")
        void defaultProxyConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getProxy());
            assertFalse(props.getProxy().isTrustProxy());
            assertNotNull(props.getProxy().getTrustedIps());
            assertTrue(props.getProxy().getTrustedIps().isEmpty());
        }

        @Test
        @DisplayName("默认Security配置应正确")
        void defaultSecurityConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getSecurity());
            assertFalse(props.getSecurity().isRequireHttps());
        }

        @Test
        @DisplayName("默认Cors配置应正确")
        void defaultCorsConfig() {
            LinkxProperties props = new LinkxProperties();

            assertNotNull(props.getCors());
            assertNotNull(props.getCors().getAllowedOrigins());
        }
    }

    @Nested
    @DisplayName("配置设置测试")
    class SettersTests {

        @Test
        @DisplayName("Jwt配置应可修改")
        void jwtConfigCanBeModified() {
            LinkxProperties props = new LinkxProperties();

            props.getJwt().setSecret("my-secret-key");
            props.getJwt().setAccessExpire(3600000L);
            props.getJwt().setRefreshExpire(604800000L);

            assertEquals("my-secret-key", props.getJwt().getSecret());
            assertEquals(3600000L, props.getJwt().getAccessExpire());
            assertEquals(604800000L, props.getJwt().getRefreshExpire());
        }

        @Test
        @DisplayName("Auth配置应可修改")
        void authConfigCanBeModified() {
            LinkxProperties props = new LinkxProperties();

            props.getAuth().setCaptchaEnabled(false);
            props.getAuth().setLoginMaxAttempts(10);
            props.getAuth().setLockDurationMinutes(30);

            assertFalse(props.getAuth().isCaptchaEnabled());
            assertEquals(10, props.getAuth().getLoginMaxAttempts());
            assertEquals(30, props.getAuth().getLockDurationMinutes());
        }

        @Test
        @DisplayName("Minio配置应可修改")
        void minioConfigCanBeModified() {
            LinkxProperties props = new LinkxProperties();

            props.getMinio().setEndpoint("http://minio.example.com:9000");
            props.getMinio().setAccessKey("new-access-key");
            props.getMinio().setSecretKey("new-secret-key");
            props.getMinio().setBucketName("new-bucket");
            props.getMinio().setMaxFileSize(50 * 1024 * 1024);

            assertEquals("http://minio.example.com:9000", props.getMinio().getEndpoint());
            assertEquals("new-access-key", props.getMinio().getAccessKey());
            assertEquals("new-secret-key", props.getMinio().getSecretKey());
            assertEquals("new-bucket", props.getMinio().getBucketName());
            assertEquals(50 * 1024 * 1024, props.getMinio().getMaxFileSize());
        }

        @Test
        @DisplayName("Proxy配置应可修改")
        void proxyConfigCanBeModified() {
            LinkxProperties props = new LinkxProperties();

            props.getProxy().setTrustProxy(true);
            List<String> trustedIps = new ArrayList<>();
            trustedIps.add("192.168.1.0/24");
            props.getProxy().setTrustedIps(trustedIps);

            assertTrue(props.getProxy().isTrustProxy());
            assertEquals(1, props.getProxy().getTrustedIps().size());
            assertEquals("192.168.1.0/24", props.getProxy().getTrustedIps().get(0));
        }
    }

    @Nested
    @DisplayName("Im内部类测试")
    class ImClassTests {

        @Test
        @DisplayName("Im配置应正确初始化")
        void imConfigInitialized() {
            LinkxProperties.Im im = new LinkxProperties.Im();

            assertEquals(8081, im.getWebsocketPort());
            assertEquals("/ws", im.getWebsocketPath());
            assertEquals(30, im.getHeartbeatIntervalSeconds());
        }

        @Test
        @DisplayName("Im配置应可修改")
        void imConfigCanBeModified() {
            LinkxProperties.Im im = new LinkxProperties.Im();

            im.setWebsocketPort(9090);
            im.setWebsocketPath("/im");
            im.setHeartbeatIntervalSeconds(60);

            assertEquals(9090, im.getWebsocketPort());
            assertEquals("/im", im.getWebsocketPath());
            assertEquals(60, im.getHeartbeatIntervalSeconds());
        }
    }

    @Nested
    @DisplayName("Cors内部类测试")
    class CorsClassTests {

        @Test
        @DisplayName("Cors配置应正确初始化")
        void corsConfigInitialized() {
            LinkxProperties.Cors cors = new LinkxProperties.Cors();

            assertNotNull(cors.getAllowedOrigins());
            assertTrue(cors.getAllowedOrigins().isEmpty());
        }

        @Test
        @DisplayName("Cors配置应可修改")
        void corsConfigCanBeModified() {
            LinkxProperties.Cors cors = new LinkxProperties.Cors();

            List<String> origins = new ArrayList<>();
            origins.add("http://localhost:3000");
            origins.add("https://example.com");
            cors.setAllowedOrigins(origins);

            assertEquals(2, cors.getAllowedOrigins().size());
            assertTrue(cors.getAllowedOrigins().contains("http://localhost:3000"));
        }
    }
}
