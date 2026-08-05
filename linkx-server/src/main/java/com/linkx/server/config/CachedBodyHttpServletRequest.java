package com.linkx.server.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 可重复读取请求体的 HttpServletRequest 包装器。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;
    private final String contentTypeOverride;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        this(request, body, null);
    }

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body, String contentTypeOverride) {
        super(request);
        this.cachedBody = body == null ? new byte[0] : body;
        this.contentTypeOverride = contentTypeOverride;
    }

    @Override
    public String getContentType() {
        if (contentTypeOverride != null) {
            return contentTypeOverride;
        }
        return super.getContentType();
    }

    @Override
    public String getHeader(String name) {
        if (contentTypeOverride != null && "Content-Type".equalsIgnoreCase(name)) {
            return contentTypeOverride;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (contentTypeOverride != null && "Content-Type".equalsIgnoreCase(name)) {
            return Collections.enumeration(Collections.singletonList(contentTypeOverride));
        }
        return super.getHeaders(name);
    }

    @Override
    public int getContentLength() {
        return cachedBody.length;
    }

    @Override
    public long getContentLengthLong() {
        return cachedBody.length;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // no-op
            }

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
