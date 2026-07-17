package com.linkx.server.service;

import com.linkx.server.entity.SysAuditLog;

/**
 * 审计日志服务接口
 */
public interface AuditLogService {

    /**
     * 记录审计日志
     *
     * @param operationType 操作类型
     * @param description  操作描述
     * @param userId      操作者用户ID
     * @param username    操作者用户名
     * @param ip          客户端IP
     * @param userAgent   User-Agent
     * @param success     是否成功
     * @param reason      失败原因（可选）
     */
    void log(SysAuditLog.OperationType operationType, String description,
              Long userId, String username, String ip, String userAgent,
              boolean success, String reason);

    /**
     * 记录审计日志（带目标资源）
     */
    void logWithTarget(SysAuditLog.OperationType operationType, String description,
                       Long userId, String username,
                       Long targetUserId, String targetUsername,
                       String targetResourceId, String targetResourceType,
                       String ip, String userAgent,
                       boolean success, String reason);

    /**
     * 记录审计日志（带额外数据）
     */
    void logWithExtra(SysAuditLog.OperationType operationType, String description,
                      Long userId, String username,
                      String ip, String userAgent,
                      boolean success, String reason, String extraData);
}
