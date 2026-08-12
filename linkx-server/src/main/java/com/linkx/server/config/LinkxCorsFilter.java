package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全局 CORS（优先于其它 Filter），确保跨域直连 8080 的上传分片等请求能读到响应头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LinkxCorsFilter extends OncePerRequestFilter {

    private static final Pattern LOCALHOST_ORIGIN =
            Pattern.compile("^http://localhost:\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOPBACK_ORIGIN =
            Pattern.compile("^http://127\\.0\\.0\\.1:\\d+$");

    private static final String EXPOSE_HEADERS =
            "Authorization,X-LinkX-Timestamp,X-LinkX-Nonce,X-LinkX-Signature";
    private static final String ALLOW_METHODS = "GET,POST,PUT,PATCH,DELETE,OPTIONS";

    private final LinkxProperties linkxProperties;
    private final Environment environment;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin) && isAllowedOrigin(origin)) {
            applyCorsHeaders(request, response, origin);
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            if (StringUtils.hasText(origin) && isAllowedOrigin(origin)) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");
        response.setHeader("Access-Control-Expose-Headers", EXPOSE_HEADERS);
        response.setHeader("Access-Control-Allow-Methods", ALLOW_METHODS);

        String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
        if (StringUtils.hasText(requestedHeaders)) {
            response.setHeader("Access-Control-Allow-Headers", requestedHeaders);
        } else {
            response.setHeader(
                    "Access-Control-Allow-Headers",
                    "Authorization,Content-Type,Accept,Origin,User-Agent,X-Requested-With,"
                            + "X-Device-Id,X-Device-Name,X-Device-Type,"
                            + "X-LinkX-Timestamp,X-LinkX-Nonce,X-LinkX-Signature,"
                            + "X-LinkX-Content-Encrypted,X-LinkX-Encrypted-Query,X-Step-Up-Token");
        }
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private boolean isAllowedOrigin(String origin) {
        String normalized = origin.trim();
        List<String> configured = linkxProperties.getCors().getAllowedOrigins();
        if (configured != null) {
            for (String allowed : configured) {
                if (allowed != null && allowed.trim().equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
        }
        if (!isLocalProfile()) {
            return false;
        }
        return LOCALHOST_ORIGIN.matcher(normalized).matches()
                || LOOPBACK_ORIGIN.matcher(normalized).matches();
    }

    private boolean isLocalProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(profiles)
                .anyMatch(p -> "local".equalsIgnoreCase(p.trim()));
    }
}
