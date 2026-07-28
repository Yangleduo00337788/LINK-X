package com.linkx.server.controller;

import com.linkx.server.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查与系统状态端点。
 * <p>
 * 匿名可访问的探针仅返回粗粒度 UP/DOWN，不泄露 MySQL/Redis 延迟等组件细节；
 * 详细组件状态仅通过 {@code /health/ready} 供编排探活（同样不返回 responseTime）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    /**
     * 公开健康检查：仅返回服务级状态，不暴露组件明细（防信息探测）。
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        boolean allUp = isMysqlUp() && isRedisUp();
        health.put("status", allUp ? "UP" : "DEGRADED");
        health.put("timestamp", Instant.now().toEpochMilli());
        health.put("service", "linkx-server");
        return Result.success(health);
    }

    @GetMapping("/live")
    public Result<Map<String, Object>> liveness() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toEpochMilli());
        return Result.success(response);
    }

    /**
     * 就绪探针：返回组件 UP/DOWN，不返回延迟数值，降低信息面。
     */
    @GetMapping("/ready")
    public Result<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        boolean mysqlOk = isMysqlUp();
        boolean redisOk = isRedisUp();

        if (mysqlOk && redisOk) {
            response.put("status", "UP");
            response.put("timestamp", Instant.now().toEpochMilli());
            return Result.success(response);
        } else {
            response.put("status", "DOWN");
            response.put("timestamp", Instant.now().toEpochMilli());
            response.put("mysql", mysqlOk ? "UP" : "DOWN");
            response.put("redis", redisOk ? "UP" : "DOWN");
            return Result.success(response);
        }
    }

    private boolean isMysqlUp() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.warn("MySQL 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isRedisUp() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
