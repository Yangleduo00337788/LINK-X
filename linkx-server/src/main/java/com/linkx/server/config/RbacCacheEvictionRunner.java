package com.linkx.server.config;

import com.linkx.server.service.RbacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时清空全部用户 RBAC Redis 缓存，避免 Flyway 权限迁移后仍命中 30 分钟旧缓存。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class RbacCacheEvictionRunner implements ApplicationRunner {

    private final RbacService rbacService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            rbacService.evictAllUserCaches();
        } catch (Exception e) {
            log.warn("启动时清除 RBAC 缓存失败（可忽略，TTL 后自动失效）: {}", e.getMessage());
        }
    }
}
