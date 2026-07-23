package com.linkx.server.service;

import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.DeviceSession;

import java.util.List;

public interface DeviceSessionService {

    DeviceSession createOrUpdate(Long userId, String deviceId, String deviceName, String deviceType, String ip, String userAgent);

    /** WebSocket 建连时注册/刷新设备会话 */
    default DeviceSession registerDevice(Long userId, String deviceId, String deviceName, String deviceType, String ip, String userAgent) {
        return createOrUpdate(userId, deviceId, deviceName, deviceType, ip, userAgent);
    }

    void updateLastActive(String deviceId);

    List<DeviceVO> listByUser(Long userId, String currentDeviceId);

    void deleteDevice(Long userId, String deviceId);

    /** WebSocket 断连时移除设备会话 */
    default void removeDevice(Long userId, String deviceId) {
        deleteDevice(userId, deviceId);
    }

    void deleteAllByUser(Long userId);
}
