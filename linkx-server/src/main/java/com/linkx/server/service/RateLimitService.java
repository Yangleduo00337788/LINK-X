package com.linkx.server.service;

import com.linkx.server.common.LoginSide;
import com.linkx.server.controller.admin.vo.AdminRateLimitHitVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface RateLimitService {

    void check(String key, int maxAttempts, int windowSeconds);

    /**
     * 检查限流（自定义超限提示），基于 Redis 原子 INCR + EXPIRE 的固定窗口计数。
     *
     * @param key           业务限流 key（不含前缀）
     * @param maxAttempts   窗口内最大允许次数
     * @param windowSeconds 窗口秒数
     * @param message       超限时抛出的提示文案
     * @throws com.linkx.server.exception.CustomException 触发限流时抛出 429
     */
    void check(String key, int maxAttempts, int windowSeconds, String message);

    /**
     * 检查登录限流：同时限制 IP 和用户名（按登录侧使用独立阈值与 Redis 键）。
     *
     * @return true 表示本次调用刚触发账号锁定（调用方应同步将用户状态改为禁用）
     */
    boolean checkLoginRateLimit(String username, HttpServletRequest request, LoginSide side);

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
    int recordLoginFailure(String username, HttpServletRequest request, LoginSide side);

    /**
     * 检查账号是否被锁定（Redis 临时锁）
     */
    boolean isAccountLocked(String username, LoginSide side);

    /**
     * 清除登录失败记录（登录成功时调用）
     */
    void clearLoginFailure(String username, LoginSide side);

    /**
     * 记录 refresh token 失败。连续失败会触发 IP 维度的锁定，
     * 防止攻击者对 refresh 接口进行暴力枚举。
     */
    void recordRefreshFailure(HttpServletRequest request);

    /** 扫描当前活跃限流计数（管理端控制台）。 */
    List<AdminRateLimitHitVO> listActiveHits(String ipFilter, int limit);

    /** 清除与指定 IP 相关的限流计数键，返回删除数量。 */
    int clearIpRateLimits(String ip);

    List<String> listWhitelist();

    void addWhitelist(String ip);

    void removeWhitelist(String ip);

    /** IP 是否在限流白名单中。 */
    boolean isWhitelisted(String ip);
}
