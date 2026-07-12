package com.linkx.server.service;

import jakarta.servlet.http.HttpServletRequest;

public interface RateLimitService {

    void check(String key, int maxAttempts, int windowSeconds);

    /**
     * 检查登录限流：同时限制 IP 和用户名
     * @param username 用户名
     * @param request HTTP请求，用于获取IP
     * @throws com.linkx.server.exception.CustomException 触发限流时抛出429
     */
    void checkLoginRateLimit(String username, HttpServletRequest request);

    /**
     * 检查注册限流：限制IP
     * @param request HTTP请求，用于获取IP
     * @throws com.linkx.server.exception.CustomException 触发限流时抛出429
     */
    void checkRegisterRateLimit(HttpServletRequest request);

    /**
     * 记录登录失败（用于账号锁定）
     * @param username 用户名
     * @param request HTTP请求
     * @return 当前失败次数
     */
    int recordLoginFailure(String username, HttpServletRequest request);

    /**
     * 检查账号是否被锁定
     * @param username 用户名
     * @param request HTTP请求
     * @return 如果锁定返回true
     */
    boolean isAccountLocked(String username, HttpServletRequest request);

    /**
     * 清除登录失败记录（登录成功时调用）
     * @param username 用户名
     * @param request HTTP请求
     */
    void clearLoginFailure(String username, HttpServletRequest request);
}
