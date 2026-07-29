package com.linkx.server.controller;

import com.linkx.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    /** 允许访问详细健康检查的内网 IP 前缀 */
    private static final Set<String> TRUSTED_IP_PREFIXES = Set.of(
            "10.",      // 10.0.0.0/8
            "172.16.",  // 172.16.0.0/12
            "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
            "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.",
            "172.29.", "172.30.", "172.31.",
            "192.168.", // 192.168.0.0/16
            "127.",     // localhost
            "0:0:0:0:0:0:0:1", "::1" // IPv6 localhost
    );

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
     * 仅允许内网 IP 访问，防止外部探测。
     */
    @GetMapping("/ready")
    public Result<Map<String, Object>> readiness(HttpServletRequest request) {
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
            // 仅内网可见详细状态
            if (isFromInternalNetwork(request)) {
                response.put("mysql", mysqlOk ? "UP" : "DOWN");
                response.put("redis", redisOk ? "UP" : "DOWN");
            }
            return Result.success(response);
        }
    }

    private boolean isFromInternalNetwork(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        for (String prefix : TRUSTED_IP_PREFIXES) {
            if (clientIp.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        // 优先使用 X-Forwarded-For（信任代理头）
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // 取最左边第一个 IP（客户端真实 IP）
            String firstIp = xff.split(",")[0].trim();
            if (StringUtils.hasText(firstIp)) {
                return firstIp;
            }
        }
        // 其次 X-Real-IP
        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) {
            return xri.trim();
        }
        // 最后 fallback 到 remoteAddr
        return request.getRemoteAddr();
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
