package com.linkx.server.config;

import com.aizuda.snailjob.client.starter.EnableSnailJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * SnailJob 客户端开关。测试环境设置 {@code snail-job.enabled=false} 可跳过注册。
 */
@Configuration
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableSnailJob
public class SnailJobClientConfiguration {
}
