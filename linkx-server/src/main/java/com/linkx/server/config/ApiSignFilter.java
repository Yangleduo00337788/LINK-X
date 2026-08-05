package com.linkx.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.admin.ApiSignNonceService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 管理端 API 轻量 HMAC 签名过滤器（仅校验完整性，不加密响应）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class ApiSignFilter extends OncePerRequestFilter {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;
    private final ApiSignNonceService apiSignNonceService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!linkxProperties.getSecurity().isApiSignEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!AdminApiSecurityPaths.isAdminApi(uri)) {
            return true;
        }
        return AdminApiSecurityPaths.isSignExcludedPath(uri);
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

        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");

        try {
            TokenType tokenType = jwtUtils.getTokenType(token);
            if (tokenType != TokenType.ACCESS) {
                writeJsonError(response, 401, "无效的访问令牌");
                return;
            }
        } catch (Exception e) {
            writeJsonError(response, 401, "未登录或登录已过期");
            return;
        }

        String timestamp = request.getHeader(ApiSignUtils.HEADER_TIMESTAMP);
        String nonce = request.getHeader(ApiSignUtils.HEADER_NONCE);
        String signature = request.getHeader(ApiSignUtils.HEADER_SIGNATURE);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            writeJsonError(response, 401, "缺少请求签名");
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            writeJsonError(response, 401, "签名时间戳无效");
            return;
        }
        long now = System.currentTimeMillis();
        long skewMs = linkxProperties.getSecurity().getApiSignTtlSeconds() * 1000L;
        if (Math.abs(now - ts) > skewMs) {
            writeJsonError(response, 401, "签名已过期");
            return;
        }

        if (!apiSignNonceService.registerNonce(nonce.trim(), Duration.ofMinutes(3))) {
            writeJsonError(response, 401, "重复请求");
            return;
        }

        byte[] bodyBytes = multipart ? new byte[0] : readBodyBytes(request);
        String bodyHash = ApiSignUtils.sha256Hex(bodyBytes);
        String path = servletPath(request);
        String encryptedQuery = request.getHeader(ApiEncryptUtils.HEADER_ENCRYPTED_QUERY);
        String queryMaterial = ApiQueryUtils.queryHashMaterial(encryptedQuery, request.getQueryString());
        String queryHash = ApiSignUtils.queryHashHex(queryMaterial);
        String payload = ApiSignUtils.buildPayload(
                timestamp.trim(),
                nonce.trim(),
                request.getMethod(),
                path,
                bodyHash,
                queryHash);

        String jti = jwtUtils.getJtiFromToken(token);
        byte[] signKey = ApiSignUtils.hexToBytes(jwtUtils.deriveApiSignKeyHex(jti));
        if (!ApiSignUtils.verifyHex(signKey, payload, signature)) {
            writeJsonError(response, 401, "签名校验失败");
            return;
        }

        HttpServletRequest wrapped = bodyBytes.length > 0
                ? new CachedBodyHttpServletRequest(request, bodyBytes)
                : request;
        filterChain.doFilter(wrapped, response);
    }

    private static byte[] readBodyBytes(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)
                && !"PUT".equalsIgnoreCase(method)
                && !"PATCH".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method)) {
            return new byte[0];
        }
        return StreamUtils.copyToByteArray(request.getInputStream());
    }

    private static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return ApiSignUtils.normalizePath(uri);
    }

    private static String readBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private void writeJsonError(HttpServletResponse response, int code, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
