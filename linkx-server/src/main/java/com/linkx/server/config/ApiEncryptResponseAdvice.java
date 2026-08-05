package com.linkx.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.util.AdminApiSecurityPaths;
import com.linkx.server.util.AdminBearerTokenResolver;
import com.linkx.server.util.ApiEncryptUtils;
import com.linkx.server.util.ApiSignUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 管理端成功响应 data 字段 AES 加密。
 */
@ControllerAdvice(basePackages = "com.linkx.server.controller.admin")
@RequiredArgsConstructor
public class ApiEncryptResponseAdvice implements ResponseBodyAdvice<Result<?>> {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;
    private final TokenCookieUtil tokenCookieUtil;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Result<?> beforeBodyWrite(
            Result<?> body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body == null || !linkxProperties.getSecurity().isApiEncryptEnabled()) {
            return body;
        }
        if (body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
            return body;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest raw = servletRequest.getServletRequest();
        String uri = raw.getRequestURI();
        if (!AdminApiSecurityPaths.isAdminApi(uri) || AdminApiSecurityPaths.isEncryptExcludedPath(uri)) {
            return body;
        }

        String token = AdminBearerTokenResolver.resolve(raw, tokenCookieUtil);
        if (!StringUtils.hasText(token)) {
            return body;
        }

        try {
            if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                return body;
            }
            String jti = jwtUtils.getJtiFromToken(token);
            byte[] key = ApiSignUtils.hexToBytes(jwtUtils.deriveApiSignKeyHex(jti));
            String plainJson = objectMapper.writeValueAsString(body.getData());
            String encrypted = ApiEncryptUtils.encryptUtf8ToBase64(key, plainJson);
            @SuppressWarnings("unchecked")
            Result<Object> mutable = (Result<Object>) body;
            mutable.setData(encrypted);
            if (response instanceof ServletServerHttpResponse servletResponse) {
                servletResponse.getServletResponse().setHeader(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1");
            }
        } catch (Exception e) {
            return body;
        }
        return body;
    }
}
