package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.util.AdminApiSecurityPaths;
import com.linkx.server.util.AdminBearerTokenResolver;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端 API 请求体 / 查询参数解密（在签名校验通过后执行）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@RequiredArgsConstructor
public class ApiEncryptFilter extends OncePerRequestFilter {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;
    private final TokenCookieUtil tokenCookieUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!linkxProperties.getSecurity().isApiEncryptEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!AdminApiSecurityPaths.isAdminApi(uri)) {
            return true;
        }
        return AdminApiSecurityPaths.isEncryptExcludedPath(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = AdminBearerTokenResolver.resolve(request, tokenCookieUtil);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                writeJsonError(response, 401, "无效的访问令牌");
                return;
            }
        } catch (Exception e) {
            writeJsonError(response, 401, "未登录或登录已过期");
            return;
        }

        if (!ApiEncryptUtils.isEncryptedRequest(request.getHeader(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED))) {
            writeJsonError(response, 400, "请求未启用加密");
            return;
        }

        byte[] signKey;
        try {
            String jti = jwtUtils.getJtiFromToken(token);
            signKey = ApiSignUtils.hexToBytes(jwtUtils.deriveApiSignKeyHex(jti));
        } catch (Exception e) {
            writeJsonError(response, 401, "未登录或登录已过期");
            return;
        }

        HttpServletRequest current = request;
        boolean multipart = isMultipart(request);

        try {
            String encryptedQueryHeader = request.getHeader(ApiEncryptUtils.HEADER_ENCRYPTED_QUERY);
            if (!StringUtils.hasText(encryptedQueryHeader)) {
                writeJsonError(response, 400, "缺少加密查询参数");
                return;
            }
            current = decryptQuery(request, signKey, encryptedQueryHeader.trim());
        } catch (Exception e) {
            writeJsonError(response, 400, "查询参数解密失败");
            return;
        }

        if (!multipart) {
            byte[] bodyBytes = readBodyBytes(request);
            if (bodyBytes.length > 0) {
                try {
                    String encrypted = ApiEncryptUtils.unwrapEncryptedBody(new String(bodyBytes, StandardCharsets.UTF_8));
                    byte[] plain = ApiEncryptUtils.decryptFromBase64(signKey, encrypted);
                    current = new CachedBodyHttpServletRequest(
                            current,
                            plain,
                            MediaType.APPLICATION_JSON_VALUE);
                } catch (Exception e) {
                    writeJsonError(response, 400, "请求体解密失败");
                    return;
                }
            }
        }

        filterChain.doFilter(current, response);
    }

    private HttpServletRequest decryptQuery(HttpServletRequest request, byte[] signKey, String encryptedQueryHeader)
            throws Exception {
        String encrypted = ApiEncryptUtils.unwrapEncryptedBody(encryptedQueryHeader);
        String plainJson = ApiEncryptUtils.decryptUtf8FromBase64(signKey, encrypted);
        Map<String, Object> raw = objectMapper.readValue(plainJson, new TypeReference<>() {});
        Map<String, String> flat = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                flat.put(k, String.valueOf(v));
            });
        }
        String canonical = ApiQueryUtils.canonicalQueryString(flat);
        return new DecryptedQueryHttpServletRequest(
                request,
                ApiQueryUtils.toParameterMap(flat),
                canonical);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
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

    private void writeJsonError(HttpServletResponse response, int code, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
