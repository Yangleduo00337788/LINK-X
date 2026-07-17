package com.linkx.server.service.impl;

import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 审计日志服务实现
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(SysAuditLog.OperationType operationType, String description,
                    Long userId, String username, String ip, String userAgent,
                    boolean success, String reason) {
        SysAuditLog log = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .ip(truncate(ip, 64))
                .userAgent(truncate(userAgent, 512))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .createTime(new Date())
                .build();
        auditLogMapper.insert(log);
    }

    @Override
    @Async
    public void logWithTarget(SysAuditLog.OperationType operationType, String description,
                              Long userId, String username,
                              Long targetUserId, String targetUsername,
                              String targetResourceId, String targetResourceType,
                              String ip, String userAgent,
                              boolean success, String reason) {
        SysAuditLog log = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .targetUserId(targetUserId)
                .targetUsername(targetUsername)
                .targetResourceId(truncate(targetResourceId, 128))
                .targetResourceType(targetResourceType)
                .ip(truncate(ip, 64))
                .userAgent(truncate(userAgent, 512))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .createTime(new Date())
                .build();
        auditLogMapper.insert(log);
    }

    @Override
    @Async
    public void logWithExtra(SysAuditLog.OperationType operationType, String description,
                             Long userId, String username,
                             String ip, String userAgent,
                             boolean success, String reason, String extraData) {
        SysAuditLog log = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .ip(truncate(ip, 64))
                .userAgent(truncate(userAgent, 512))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .extraData(truncate(extraData, 2048))
                .createTime(new Date())
                .build();
        auditLogMapper.insert(log);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
