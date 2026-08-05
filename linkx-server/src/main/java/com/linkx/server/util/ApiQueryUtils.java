package com.linkx.server.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 管理端 API 查询参数规范化（签名 / 解密后注入）。
 */
public final class ApiQueryUtils {

    private ApiQueryUtils() {
    }

    public static String canonicalQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((k, v) -> {
            if (!StringUtils.hasText(k) || v == null) {
                return;
            }
            sorted.put(k.trim(), v);
        });
        if (sorted.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(k)).append('=').append(urlEncode(v));
        });
        return sb.toString();
    }

    public static Map<String, String> parseQueryString(String queryString) {
        if (!StringUtils.hasText(queryString)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            out.put(urlDecode(key), urlDecode(value));
        }
        return out;
    }

    public static Map<String, String[]> toParameterMap(Map<String, String> flat) {
        if (flat == null || flat.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String[]> out = new LinkedHashMap<>();
        flat.forEach((k, v) -> out.put(k, new String[] {v}));
        return out;
    }

    public static Map<String, String> fromRequest(HttpServletRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        if (request == null) {
            return out;
        }
        request.getParameterMap().forEach((k, values) -> {
            if (values != null && values.length > 0) {
                out.put(k, values[0]);
            }
        });
        return out;
    }

    public static String queryHashMaterial(String encryptedQueryHeader, String rawQueryString) {
        if (StringUtils.hasText(encryptedQueryHeader)) {
            return encryptedQueryHeader.trim();
        }
        return canonicalQueryString(parseQueryString(rawQueryString));
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
