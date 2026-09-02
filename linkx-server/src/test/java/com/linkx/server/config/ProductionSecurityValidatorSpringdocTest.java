package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.linkx.server.storage.ObjectStorageRouter;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 校验生产环境对 Springdoc/Swagger 的 fail-closed 强制关闭：
 * 无论 env 来源（springdoc.api-docs.enabled 或 SPRINGDOC_ENABLED）如何，缺省/开启都必须拒绝启动。
 */
class ProductionSecurityValidatorSpringdocTest {

    private static final String SPRINGDOC_ERROR_MARKER = "SPRINGDOC_ENABLED";

    @Test
    void prod_springdocMissing_isRejected() {
        Environment env = mock(Environment.class);
        when(env.getProperty("springdoc.api-docs.enabled")).thenReturn(null);
        when(env.getProperty("SPRINGDOC_ENABLED")).thenReturn(null);

        List<String> errors = run(env);
        assertTrue(containsMarker(errors), "缺省未显式关闭时应拒绝启动: " + errors);
    }

    @Test
    void prod_springdocEnabled_isRejected() {
        Environment env = mock(Environment.class);
        when(env.getProperty("springdoc.api-docs.enabled")).thenReturn(null);
        when(env.getProperty("SPRINGDOC_ENABLED")).thenReturn("true");

        List<String> errors = run(env);
        assertTrue(containsMarker(errors), "显式开启时应拒绝启动: " + errors);
    }

    @Test
    void prod_springdocDisabled_passes_check() {
        Environment env = mock(Environment.class);
        when(env.getProperty("springdoc.api-docs.enabled")).thenReturn(null);
        when(env.getProperty("SPRINGDOC_ENABLED")).thenReturn("false");

        List<String> errors = run(env);
        assertFalse(containsMarker(errors), "显式关闭时不应报 Springdoc 相关错误: " + errors);
    }

    @Test
    void prod_springdocConfiguredViaYmlDisabled_passes_check() {
        // springdoc.api-docs.enabled 由 application.yml 自 SPRINGDOC_ENABLED 映射而来
        Environment env = mock(Environment.class);
        when(env.getProperty("springdoc.api-docs.enabled")).thenReturn("false");
        when(env.getProperty("SPRINGDOC_ENABLED")).thenReturn(null);

        List<String> errors = run(env);
        assertFalse(containsMarker(errors), "经 yml 映射关闭时不应报 Springdoc 相关错误: " + errors);
    }

    private static List<String> run(Environment env) {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                new LinkxProperties(),
                env,
                mock(ObjectStorageRouter.class));
        return validator.collectErrors();
    }

    private static boolean containsMarker(List<String> errors) {
        return errors.stream().anyMatch(e -> e != null && e.contains(SPRINGDOC_ERROR_MARKER));
    }
}