package com.linkx.server.service;

import com.linkx.server.config.LinkxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BizRateLimitPolicyResolver 策略映射")
class BizRateLimitPolicyResolverTest {

    private BizRateLimitPolicyResolver resolver;
    private LinkxProperties props;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        LinkxProperties.Auth auth = props.getAuth();
        auth.setRateLimitSearchPerMinute(31);
        auth.setRateLimitListPerMinute(61);
        auth.setRateLimitWritePerMinute(31);
        auth.setRateLimitUploadPerMinute(21);
        resolver = new BizRateLimitPolicyResolver(props);
    }

    @Test
    void categorizeScopes() {
        assertEquals(BizRateLimitCategory.SEARCH, resolver.categorize("friend:search"));
        assertEquals(BizRateLimitCategory.LIST, resolver.categorize("moments:list"));
        assertEquals(BizRateLimitCategory.LIST, resolver.categorize("media:avatar"));
        assertEquals(BizRateLimitCategory.UPLOAD, resolver.categorize("chat:upload"));
        assertEquals(BizRateLimitCategory.WRITE, resolver.categorize("moments:publish"));
    }
}
