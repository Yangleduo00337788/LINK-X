package com.linkx.server.service;

/**
 * 管理端「业务接口限流」策略分类，与 {@link com.linkx.server.config.LinkxProperties.Auth} 中配置对应。
 */
public enum BizRateLimitCategory {
    SEARCH,
    LIST,
    WRITE,
    UPLOAD
}
