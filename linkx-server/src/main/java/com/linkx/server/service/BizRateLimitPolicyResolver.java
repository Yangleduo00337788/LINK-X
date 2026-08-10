package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RateLimit;
import com.linkx.server.config.LinkxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 将 {@link RateLimit} 注解映射到管理端可配置的每分钟限流阈值。
 */
@Component
@RequiredArgsConstructor
public class BizRateLimitPolicyResolver {

    /** 非标准窗口（如 1s / 5min）或独立配额，保留注解原值 */
    private static final Set<String> ANNOTATION_ONLY_SCOPES = Set.of(
            "conference:signal",
            "compliance:purge"
    );

    private final LinkxProperties linkxProperties;

    public record ResolvedLimit(int maxAttempts, int windowSeconds, BizRateLimitCategory category) {
    }

    public ResolvedLimit resolve(RateLimit annotation) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        if (annotation == null) {
            return new ResolvedLimit(auth.getRateLimitListPerMinute(), 60, BizRateLimitCategory.LIST);
        }
        String scope = annotation.scope();
        if (ANNOTATION_ONLY_SCOPES.contains(scope) || annotation.window() != 60) {
            return new ResolvedLimit(annotation.value(), annotation.window(), categorize(scope));
        }
        BizRateLimitCategory category = categorize(scope);
        return new ResolvedLimit(maxFor(category, auth), 60, category);
    }

    public BizRateLimitCategory categorize(String scope) {
        if (!StringUtils.hasText(scope)) {
            return BizRateLimitCategory.WRITE;
        }
        String s = scope.toLowerCase();
        if (s.contains("upload")) {
            return BizRateLimitCategory.UPLOAD;
        }
        if (s.contains("search")) {
            return BizRateLimitCategory.SEARCH;
        }
        if (isListScope(s)) {
            return BizRateLimitCategory.LIST;
        }
        return BizRateLimitCategory.WRITE;
    }

    private static boolean isListScope(String scope) {
        return scope.contains(":list")
                || scope.endsWith(":list")
                || scope.contains(":detail")
                || scope.contains(":view")
                || scope.contains(":content")
                || scope.contains(":download")
                || scope.startsWith("media:")
                || scope.equals("compliance:export");
    }

    private static int maxFor(BizRateLimitCategory category, LinkxProperties.Auth auth) {
        switch (category) {
            case SEARCH:
                return auth.getRateLimitSearchPerMinute();
            case LIST:
                return auth.getRateLimitListPerMinute();
            case UPLOAD:
                return auth.getRateLimitUploadPerMinute();
            case WRITE:
            default:
                return auth.getRateLimitWritePerMinute();
        }
    }
}
