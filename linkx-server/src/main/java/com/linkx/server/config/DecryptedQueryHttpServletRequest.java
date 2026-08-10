package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将解密后的查询参数注入请求，避免 URL 明文暴露筛选条件。
 */
public class DecryptedQueryHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String[]> parameterMap;
    private final String queryString;

    public DecryptedQueryHttpServletRequest(HttpServletRequest request, Map<String, String[]> parameterMap, String queryString) {
        super(request);
        this.parameterMap = parameterMap == null ? Collections.emptyMap() : new LinkedHashMap<>(parameterMap);
        this.queryString = queryString == null ? "" : queryString;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameterMap);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }
}
