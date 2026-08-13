package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.storage.ObjectStorageRouter;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final ObjectStorageRouter objectStorageRouter;

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

        // 媒体代理响应不要套页面级 CSP，避免个别浏览器对子资源策略表现异常
        if (!isMediaProxyPath(request)) {
            String mediaOrigins = String.join(" ", objectStorageRouter.mediaOriginsForCsp());
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
                            + "script-src 'self' 'unsafe-inline'; "
                            + "style-src 'self' 'unsafe-inline'; "
                            + "connect-src 'self' %s %s;",
                    mediaOrigins, mediaOrigins,
                    wsOrigins, mediaOrigins
            );
            response.setHeader("Content-Security-Policy", csp);
        }

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
        // context-path=/api 时为 /api/media/external、/api/media/avatars/...、/api/media/banners/...
        return uri.contains("/media/external")
                || uri.contains("/media/stored")
                || uri.contains("/media/avatars/")
                || uri.contains("/media/moments-background/")
                || uri.contains("/media/banners/")
                || uri.contains("/media/recommends/")
                || uri.contains("/media/activities/");
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
