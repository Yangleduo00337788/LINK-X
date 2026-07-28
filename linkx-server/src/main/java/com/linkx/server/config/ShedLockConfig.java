package com.linkx.server.config;

import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 分布式定时任务锁配置。
 * <p>
 * 多实例部署时，通过 Redis 互斥保证同一时刻仅一个实例执行 @SchedulerLock 标记的任务，
 * 避免红包过期退款、消息留存清理、群禁言调度等任务重复执行造成数据错乱。
 * </p>
 * 锁键命名空间：linkx-shedlock
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    private static final String LOCK_KEY_PREFIX = "linkx-shedlock";

    @Bean
    public RedisLockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, LOCK_KEY_PREFIX);
    }
}
