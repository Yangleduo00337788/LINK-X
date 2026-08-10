package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import java.util.List;

public interface LoginAuditService {

    void record(Long userId, String username, String ip, String userAgent, boolean success, String reason);

    /**
     * 查询用户近期成功登录 IP（去重，按时间倒序）。
     * 用于管理端新 IP 登录提示。
     */
    List<String> recentSuccessfulIps(Long userId, int limit);
}
