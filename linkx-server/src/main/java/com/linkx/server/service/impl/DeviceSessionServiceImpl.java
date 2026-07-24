package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.im.ImChannelManager;
import com.linkx.server.mapper.DeviceSessionMapper;
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
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final AuditLogService auditLogService;

    @Override
    public DeviceSession createOrUpdate(Long userId, String deviceId, String deviceName, String deviceType, String ip, String userAgent) {
        DeviceSession session = deviceSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(deviceId)
        );

        if (session == null) {
            session = DeviceSession.builder()
                    .userId(userId)
                    .deviceId(deviceId != null ? deviceId : UUID.randomUUID().toString())
                    .deviceName(deviceName)
                    .deviceType(deviceType)
                    .ip(ip)
                    .userAgent(userAgent)
                    .lastActive(new Date())
                    .createTime(new Date())
                    .build();
            deviceSessionMapper.insert(session);
        } else {
            session.setLastActive(new Date());
            if (deviceName != null) session.setDeviceName(deviceName);
            if (deviceType != null) session.setDeviceType(deviceType);
            deviceSessionMapper.update(session);
        }
        return session;
    }

    @Override
    public void updateLastActive(String deviceId) {
        DeviceSession session = deviceSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getDeviceId).eq(deviceId)
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
                        .ip(s.getIp())
                        .lastActive(s.getLastActive())
                        .current(currentDeviceId != null && s.getDeviceId().equals(currentDeviceId))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDevice(Long userId, String deviceId) {
        deviceSessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(DeviceSession::getUserId).eq(userId)
                        .and(DeviceSession::getDeviceId).eq(deviceId)
        );
    }

    @Override
    public void kickDevice(Long userId, String deviceId, String operatorUsername, String ip, String userAgent) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return;
        }
        String normalized = deviceId.trim();
        tokenService.revokeDeviceTokens(userId, normalized);
        int closed = channelManager.disconnectDevice(userId, normalized);
        deleteDevice(userId, normalized);
        auditLogService.logWithTarget(
                SysAuditLog.OperationType.DEVICE_KICK,
                "踢设备下线: " + normalized + " (断开连接 " + closed + ")",
                userId,
                operatorUsername,
                userId,
                operatorUsername,
                normalized,
                "device",
                ip,
                userAgent,
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
}
