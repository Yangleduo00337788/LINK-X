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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查与系统状态端点
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toEpochMilli());
        health.put("service", "linkx-server");

        // 检查 MySQL
        health.put("mysql", checkMysql());

        // 检查 Redis
        health.put("redis", checkRedis());

        // 计算总体状态
        boolean allUp = "UP".equals(((Map<?, ?>) health.get("mysql")).get("status"))
                && "UP".equals(((Map<?, ?>) health.get("redis")).get("status"));

        health.put("status", allUp ? "UP" : "DEGRADED");

        return Result.success(health);
    }

    @GetMapping("/live")
    public Result<Map<String, Object>> liveness() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toEpochMilli());
        return Result.success(response);
    }

    @GetMapping("/ready")
    public Result<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        boolean mysqlOk = checkMysql().get("status").equals("UP");
        boolean redisOk = checkRedis().get("status").equals("UP");

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

    private Map<String, Object> checkMysql() {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            result.put("status", valid ? "UP" : "DOWN");
            result.put("responseTime", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("MySQL 健康检查失败: {}", e.getMessage());
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            result.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
            result.put("responseTime", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }
}
