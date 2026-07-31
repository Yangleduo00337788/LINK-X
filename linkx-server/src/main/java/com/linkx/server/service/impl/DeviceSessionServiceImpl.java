package com.linkx.server.service.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.entity.SysUser;
import com.linkx.server.im.ImChannelManager;
import com.linkx.server.mapper.DeviceSessionMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.AuditLogService;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.TokenService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceSessionServiceImpl extends ServiceImpl<DeviceSessionMapper, DeviceSession> implements DeviceSessionService {

    private final DeviceSessionMapper deviceSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final AuditLogService auditLogService;

    @Override
    public DeviceSession createOrUpdate(Long userId, String deviceId, String deviceName, String deviceType, String ip, String userAgent) {
        String normalized = normalizeDeviceId(deviceId);
        if (normalized == null) {
            normalized = UUID.randomUUID().toString();
        }

        DeviceSession session = deviceSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(normalized)
        );

        if (session == null) {
            session = DeviceSession.builder()
                    .userId(userId)
                    .deviceId(normalized)
                    .deviceName(deviceName)
                    .deviceType(deviceType)
                    .ip(ClientIpResolver.normalizeToIpv4(ip))
                    .userAgent(sanitizeUserAgent(userAgent))
                    .lastActive(new Date())
                    .createTime(new Date())
                    .build();
            deviceSessionMapper.insert(session);
        } else {
            session.setLastActive(new Date());
            if (deviceName != null) session.setDeviceName(deviceName);
            if (deviceType != null) session.setDeviceType(deviceType);
            if (ip != null) session.setIp(ClientIpResolver.normalizeToIpv4(ip));
            if (StringUtils.hasText(userAgent)) session.setUserAgent(sanitizeUserAgent(userAgent));
            deviceSessionMapper.update(session);
        }
        return session;
    }

    @Override
    public void updateLastActive(Long userId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return;
        }
        DeviceSession session = deviceSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(normalized)
                        .limit(1)
        );
        if (session != null) {
            session.setLastActive(new Date());
            deviceSessionMapper.update(session);
        }
    }

    @Override
    public List<DeviceVO> listByUser(Long userId, String currentDeviceId) {
        List<DeviceSession> sessions = deviceSessionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .orderBy(DeviceSession::getLastActive, false)
        );

        return sessions.stream()
                .map(s -> DeviceVO.builder()
                        .id(s.getDeviceId())
                        .deviceName(s.getDeviceName() != null ? s.getDeviceName() : "未知设备")
                        .deviceType(s.getDeviceType() != null ? s.getDeviceType() : "Web")
                        .ip(maskIp(s.getIp()))
                        .userAgent(maskUserAgent(s.getUserAgent()))
                        .lastActive(s.getLastActive())
                        .current(currentDeviceId != null && s.getDeviceId().equals(currentDeviceId))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDevice(Long userId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return;
        }
        deviceSessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(normalized)
        );
    }

    @Override
    public void kickDevice(Long userId, String deviceId, Long operatorId, String operatorUsername, String ip, String userAgent) {
        String normalized = normalizeDeviceId(deviceId);
        if (userId == null || normalized == null) {
            return;
        }
        tokenService.revokeDeviceTokens(userId, normalized);
        int closed = channelManager.disconnectDevice(userId, normalized);
        deleteDevice(userId, normalized);

        SysUser target = sysUserMapper.selectOneById(userId);
        String targetUsername = target != null && StringUtils.hasText(target.getUsername())
                ? target.getUsername()
                : String.valueOf(userId);

        Long resolvedOperatorId = operatorId != null ? operatorId : userId;
        String resolvedOperatorName = StringUtils.hasText(operatorUsername) ? operatorUsername.trim() : null;
        if (!StringUtils.hasText(resolvedOperatorName)) {
            if (resolvedOperatorId.equals(userId)) {
                resolvedOperatorName = targetUsername;
            } else {
                SysUser operator = sysUserMapper.selectOneById(resolvedOperatorId);
                resolvedOperatorName = operator != null && StringUtils.hasText(operator.getUsername())
                        ? operator.getUsername()
                        : String.valueOf(resolvedOperatorId);
            }
        }

        auditLogService.logWithTarget(
                SysAuditLog.OperationType.DEVICE_KICK,
                "踢设备下线: " + normalized + " (断开连接 " + closed + ")",
                resolvedOperatorId,
                resolvedOperatorName,
                userId,
                targetUsername,
                normalized,
                "device",
                ip,
                sanitizeUserAgent(userAgent),
                true,
                null
        );
    }

    @Override
    public void deleteAllByUser(Long userId) {
        deviceSessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
        );
    }

    private String normalizeDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        String normalized = deviceId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sanitizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String sanitized = userAgent.trim();
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String trimmed = ClientIpResolver.normalizeToIpv4(ip.trim());
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon > 0 && trimmed.indexOf(':') != lastColon) {
            return "[redacted-ipv6]";
        }
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot > 0) {
            return trimmed.substring(0, lastDot + 1) + "*";
        }
        return "[redacted]";
    }

    private String maskUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String sanitized = userAgent.trim();
        if (sanitized.length() <= 64) {
            return sanitized;
        }
        return sanitized.substring(0, 61) + "...";
    }
}
