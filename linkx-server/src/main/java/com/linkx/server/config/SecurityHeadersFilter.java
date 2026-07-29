package com.linkx.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTPS 强制与安全响应头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (linkxProperties.getSecurity().isRequireHttps() && !isSecureRequest(request)) {
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, 403, "请使用 HTTPS 访问");
            return;
        }

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // 媒体代理自带短时 Cache-Control，避免全局 no-store 覆盖导致重复代拉
        if (!isMediaProxyPath(request)) {
            response.setHeader("Cache-Control", "no-store");
        }
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        response.setHeader("Permissions-Policy", "geolocation=(self), microphone=(self), camera=(self)");

        // 生产 HTTPS 部署仅允许同源 WebSocket；localhost 仅用于显式的本地开发模式。
        String minioOrigin = linkxProperties.getMinio().getEndpoint();
        int wsPort = linkxProperties.getIm().getWebsocketPort();
        String wsOrigins = linkxProperties.getSecurity().isRequireHttps()
                ? ""
                : String.format("ws://127.0.0.1:%d ws://localhost:%d", wsPort, wsPort);
        String csp = String.format(
                "default-src 'self'; "
                        + "img-src 'self' data: blob: %s; "
                        + "media-src 'self' data: blob: %s; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'; "
                        + "frame-ancestors 'none'; "
                        // [P2-S2] script-src 保留 'unsafe-inline'：Vue 运行时模板编译需要内联脚本，
                        // 移除会导致前端白屏。需前端全编译构建（runtime-only + 预编译模板）后移除。
                        + "script-src 'self' 'unsafe-inline'; "
                        // [P2-S2] style-src 保留 'unsafe-inline'：NaiveUI 等组件库运行时动态注入样式需要，
                        // 移除会导致样式错乱。需前端构建期抽取样式为外部文件后移除。
                        + "style-src 'self' 'unsafe-inline'; "
                        + "connect-src 'self' %s %s;",
                minioOrigin, minioOrigin,
                wsOrigins, minioOrigin
        );
        response.setHeader("Content-Security-Policy", csp);

        if (linkxProperties.getSecurity().isRequireHttps()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isMediaProxyPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        // context-path=/api 时为 /api/media/external
        return uri.endsWith("/media/external") || uri.contains("/media/external?");
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        // 仅当信任反向代理时才采纳 X-Forwarded-Proto，防止直连伪造绕过 HTTPS 强制
        if (!linkxProperties.getProxy().isTrustProxy()) {
            return false;
        }
        var trusted = linkxProperties.getProxy().getTrustedIps();
        if (trusted != null && !trusted.isEmpty() && !trusted.contains(request.getRemoteAddr())) {
            return false;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }

    private void writeJsonError(
            HttpServletResponse response,
            int httpStatus,
            int code,
            String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
