package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.util.AdminApiSecurityPaths;
import com.linkx.server.util.ApiEncryptUtils;
import com.linkx.server.util.ApiQueryUtils;
import com.linkx.server.util.ApiSignUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 将 CSV / 二进制导出响应包装为加密 JSON，避免 Network 明文泄露。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class ApiEncryptBinaryResponseFilter extends OncePerRequestFilter {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!linkxProperties.getSecurity().isApiEncryptEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        return !AdminApiSecurityPaths.isAdminApi(uri)
                || !AdminApiSecurityPaths.isBinaryEncryptResponsePath(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = readBearerToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapped);

        if (!shouldWrapResponse(request, wrapped)) {
            wrapped.copyBodyToResponse();
            return;
        }

        try {
            if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                wrapped.copyBodyToResponse();
                return;
            }
            byte[] body = wrapped.getContentAsByteArray();
            if (body.length == 0) {
                wrapped.copyBodyToResponse();
                return;
            }
            String jti = jwtUtils.getJtiFromToken(token);
            byte[] key = ApiSignUtils.hexToBytes(jwtUtils.deriveApiSignKeyHex(jti));
            String encrypted = ApiEncryptUtils.encryptToBase64(key, body);
            wrapped.resetBuffer();
            wrapped.setCharacterEncoding(StandardCharsets.UTF_8.name());
            wrapped.setContentType(MediaType.APPLICATION_JSON_VALUE);
            wrapped.setHeader(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1");
            wrapped.getWriter().write(objectMapper.writeValueAsString(Result.success(encrypted)));
        } catch (Exception e) {
            wrapped.resetBuffer();
        }
        wrapped.copyBodyToResponse();
    }

    private static boolean shouldWrapResponse(HttpServletRequest request, ContentCachingResponseWrapper response) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return false;
        }
        if ("1".equals(response.getHeader(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED))) {
            return false;
        }
        String contentType = response.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            return false;
        }
        return AdminApiSecurityPaths.isBinaryEncryptResponsePath(request.getRequestURI());
    }

    private static String readBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
