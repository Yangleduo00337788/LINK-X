package com.linkx.server.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class CachedBodyHttpServletRequestTest {

    @Test
    void overridesContentTypeForHeaderAndHeadersEnumeration() {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.setMethod("PUT");
        original.setContentType("text/plain;charset=UTF-8");
        original.setContent("encrypted".getBytes(StandardCharsets.UTF_8));

        byte[] body = "{\"apiEncryptEnabled\":true}".getBytes(StandardCharsets.UTF_8);
        HttpServletRequest wrapped = new CachedBodyHttpServletRequest(
                original,
                body,
                MediaType.APPLICATION_JSON_VALUE);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, wrapped.getContentType());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, wrapped.getHeader("Content-Type"));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, wrapped.getHeader("content-type"));
        assertIterableEquals(
                Collections.singletonList(MediaType.APPLICATION_JSON_VALUE),
                Collections.list(wrapped.getHeaders("Content-Type")));
        assertEquals(body.length, wrapped.getContentLength());
    }
}
