package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 .env.* 再注入 Spring Environment（与 {@link DotEnvBootstrap} 互补）。
 * 若 main 已通过 System.setProperty 注入，此处对已有键会跳过。
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "linkxDotEnv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        DotEnvLoader.Result result = DotEnvLoader.load();
        if (!result.found() || result.values().isEmpty()) {
            return;
        }
        Map<String, Object> effective = new LinkedHashMap<>();
        for (var e : result.values().entrySet()) {
            String key = e.getKey();
            String value = e.getValue() != null ? e.getValue() : "";
            // 测试不走 main/Bootstrap：同步写入 System properties，保证 ${VAR} 占位符可解析
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
            if (environment.getProperty(key) == null) {
                effective.put(key, value);
            }
        }
        if (!effective.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, effective));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
