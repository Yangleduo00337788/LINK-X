package com.linkx.server.service;

public interface LoginAuditService {

    void record(Long userId, String username, String ip, String userAgent, boolean success, String reason);
}
