package com.linkx.server.config.metrics;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer + Prometheus 配置
 */
@Configuration
public class MetricsConfig {

    /**
     * 启用 @Timed 注解支持
     * 在方法上添加 @Timed 注解即可自动记录耗时
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
