package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 向 Swagger UI HTML 注入中英文切换脚本（不改动 webjar 源文件）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SwaggerUiI18nFilter extends OncePerRequestFilter {

    private static final String SCRIPT_FMT =
            "<script src=\"%s/swagger-i18n.js\" defer></script>";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // context-path=/api 时 URI 形如 /api/swagger-ui/index.html
        return path == null || !path.contains("/swagger-ui/index.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);

        byte[] body = wrapper.getContentAsByteArray();
        String contentType = wrapper.getContentType();
        if (body.length > 0 && contentType != null && contentType.contains("text/html")) {
            String html = new String(body, StandardCharsets.UTF_8);
            if (!html.contains("swagger-i18n.js") && html.contains("</body>")) {
                String ctx = request.getContextPath() == null ? "" : request.getContextPath();
                String script = SCRIPT_FMT.formatted(ctx);
                html = html.replace("</body>", script + "\n</body>");
                byte[] updated = html.getBytes(StandardCharsets.UTF_8);
                response.setContentLength(updated.length);
                response.getOutputStream().write(updated);
                return;
            }
        }
        wrapper.copyBodyToResponse();
    }
}
