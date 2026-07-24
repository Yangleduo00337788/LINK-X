package com.linkx.server.config;

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
            if (environment.getProperty(e.getKey()) == null) {
                effective.put(e.getKey(), e.getValue());
            }
        }
        if (!effective.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, effective));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
