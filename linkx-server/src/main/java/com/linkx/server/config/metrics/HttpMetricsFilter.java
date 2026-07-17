package com.linkx.server.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求指标过滤器
 * 记录每个 API 请求的耗时和响应状态
 */
@Component
@RequiredArgsConstructor
public class HttpMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 跳过非 API 路径和静态资源
        if (!path.startsWith("/api") || path.startsWith("/api/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);

            // 记录 HTTP 请求指标
            Timer.builder("http.server.requests")
                    .description("HTTP 请求耗时")
                    .tag("method", method)
                    .tag("uri", normalizeUri(path))
                    .tag("status", String.valueOf(response.getStatus()))
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS);

            // 记录活动会话数（如果有用户信息）
            if (request.getAttribute("userId") != null) {
                meterRegistry.counter("linkx.active.sessions").increment();
            }
        }
    }

    /**
     * 标准化 URI 路径（替换动态参数）
     */
    private String normalizeUri(String uri) {
        // 替换数字 ID
        String normalized = uri.replaceAll("/\\d+", "/{id}");
        // 替换 UUID
        normalized = normalized.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{uuid}");
        return normalized;
    }
}
