package com.linkx.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("生产安全启动校验")
class ProductionSecurityValidatorTest {

    @Mock
    private Environment environment;

    private LinkxProperties props;
    private ProductionSecurityValidator validator;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        props.getSecurity().setRequireHttps(true);
        props.getApp().setDevModeEnabled(false);
        props.getAuth().setAdminTotpRequired(true);
        props.getAuth().setAdminCaptchaEnabled(true);
        props.getCors().setAllowedOrigins(List.of("https://admin.example.com"));
        props.getJwt().setSecret("Prod-JWT-Secret-Key-At-Least-32-Bytes!!");
        props.getMinio().setAccessKey("linkx-prod-access");
        props.getMinio().setSecretKey("Prod-Minio-Secret-Key-9x");
        props.getMinio().setEndpoint("https://minio.example.com");

        when(environment.getProperty("spring.datasource.password")).thenReturn("Prod-Db-Pass-9x!");
        when(environment.getProperty("DB_PASSWORD")).thenReturn(null);
        when(environment.getProperty("spring.data.redis.password")).thenReturn("Prod-Redis-Pass-9x!");
        when(environment.getProperty("REDIS_PASSWORD")).thenReturn(null);

        validator = new ProductionSecurityValidator(props, environment);
    }

    @Test
    @DisplayName("合格配置无错误")
    void healthyConfigHasNoErrors() {
        assertTrue(validator.collectErrors().isEmpty());
    }

    @Test
    @DisplayName("REQUIRE_HTTPS=false 失败")
    void requireHttpsMustBeTrue() {
        props.getSecurity().setRequireHttps(false);
        assertTrue(validator.collectErrors().stream().anyMatch(e -> e.contains("REQUIRE_HTTPS")));
    }

    @Test
    @DisplayName("弱 JWT / 空 DB 密码失败")
    void weakSecretsFail() {
        props.getJwt().setSecret("changeme");
        when(environment.getProperty("spring.datasource.password")).thenReturn("");
        List<String> errors = validator.collectErrors();
        assertTrue(errors.stream().anyMatch(e -> e.contains("JWT_SECRET")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("DB_PASSWORD")));
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("默认 minioadmin 失败")
    void defaultMinioAdminFails() {
        props.getMinio().setAccessKey("minioadmin");
        props.getMinio().setSecretKey("minioadmin");
        assertTrue(validator.collectErrors().stream().anyMatch(e -> e.contains("MINIO")));
    }

    @Test
    @DisplayName("启用 SnailJob 时示例 Token 失败")
    void exampleSnailJobTokenFails() {
        when(environment.getProperty("snail-job.enabled")).thenReturn("true");
        when(environment.getProperty("SNAIL_JOB_ENABLED")).thenReturn(null);
        when(environment.getProperty("snail-job.token")).thenReturn("SJ_Wyz3dmsdbDOkDujOTSSoBjGQP1BMsVnj");
        when(environment.getProperty("SNAIL_JOB_TOKEN")).thenReturn(null);
        assertTrue(validator.collectErrors().stream().anyMatch(e -> e.contains("SNAIL_JOB_TOKEN")));
    }
}
