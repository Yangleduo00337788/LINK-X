package com.linkx.server.service;

import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务测试
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogMapper);
    }

    @Test
    void shouldLogBasicAuditInfo() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.LOGIN;
        String description = "用户登录";
        Long userId = 12345L;
        String username = "testuser";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        boolean success = true;
        String reason = null;

        // When
        auditLogService.log(operationType, description, userId, username, ip, userAgent, success, reason);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertEquals("LOGIN", capturedLog.getOperationType());
        assertEquals("用户登录", capturedLog.getDescription());
        assertEquals(userId, capturedLog.getUserId());
        assertEquals(username, capturedLog.getUsername());
        assertEquals(ip, capturedLog.getIp());
        assertEquals(userAgent, capturedLog.getUserAgent());
        assertEquals("SUCCESS", capturedLog.getStatus());
        assertNull(capturedLog.getFailureReason());
    }

    @Test
    void shouldLogFailedOperation() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.LOGIN;
        String description = "用户登录";
        Long userId = 12345L;
        String username = "testuser";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        boolean success = false;
        String reason = "密码错误";

        // When
        auditLogService.log(operationType, description, userId, username, ip, userAgent, success, reason);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertEquals("FAIL", capturedLog.getStatus());
        assertEquals("密码错误", capturedLog.getFailureReason());
    }

    @Test
    void shouldLogWithTargetUser() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.DELETE_FRIEND;
        String description = "删除好友";
        Long userId = 12345L;
        String username = "testuser";
        Long targetUserId = 67890L;
        String targetUsername = "friend";
        String targetResourceId = "conv_123";
        String targetResourceType = "conversation";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        boolean success = true;
        String reason = null;

        // When
        auditLogService.logWithTarget(operationType, description, userId, username,
                targetUserId, targetUsername, targetResourceId, targetResourceType,
                ip, userAgent, success, reason);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertEquals(targetUserId, capturedLog.getTargetUserId());
        assertEquals(targetUsername, capturedLog.getTargetUsername());
        assertEquals(targetResourceId, capturedLog.getTargetResourceId());
        assertEquals(targetResourceType, capturedLog.getTargetResourceType());
    }

    @Test
    void shouldLogWithExtraData() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.SEND_MESSAGE;
        String description = "发送消息";
        Long userId = 12345L;
        String username = "testuser";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        boolean success = true;
        String reason = null;
        String extraData = "{\"conversationId\":123,\"messageType\":\"text\"}";

        // When
        auditLogService.logWithExtra(operationType, description, userId, username,
                ip, userAgent, success, reason, extraData);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertEquals(extraData, capturedLog.getExtraData());
    }

    @Test
    void shouldTruncateLongValues() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.UPDATE_PROFILE;
        String description = "更新资料";
        Long userId = 12345L;
        String username = "testuser";
        String longIp = "192.168.1.1";
        String longUserAgent = "A".repeat(600); // 超过 512 字符
        boolean success = true;
        String reason = null;

        // When
        auditLogService.log(operationType, description, userId, username, longIp, longUserAgent, success, reason);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertEquals(512, capturedLog.getUserAgent().length()); // 应该被截断到 512
    }

    @Test
    void shouldHandleNullValues() {
        // Given
        SysAuditLog.OperationType operationType = SysAuditLog.OperationType.LOGOUT;
        String description = "用户登出";
        Long userId = null;
        String username = null;
        String ip = "192.168.1.1";
        String userAgent = null;
        boolean success = true;
        String reason = null;

        // When
        auditLogService.log(operationType, description, userId, username, ip, userAgent, success, reason);

        // Then
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        SysAuditLog capturedLog = captor.getValue();
        assertNull(capturedLog.getUserId());
        assertNull(capturedLog.getUsername());
        assertNull(capturedLog.getUserAgent());
    }

    @Test
    void shouldLogAllOperationTypes() {
        // 测试所有操作类型都能正确记录
        for (SysAuditLog.OperationType operationType : SysAuditLog.OperationType.values()) {
            auditLogService.log(operationType, "测试操作",
                    1L, "testuser", "127.0.0.1", "TestAgent", true, null);

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(auditLogMapper, atLeastOnce()).insert(captor.capture());

            SysAuditLog capturedLog = captor.getValue();
            assertEquals(operationType.name(), capturedLog.getOperationType());
        }
    }
}
