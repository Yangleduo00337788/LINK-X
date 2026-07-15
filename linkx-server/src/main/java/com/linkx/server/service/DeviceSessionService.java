package com.linkx.server.service;

import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.entity.DeviceSession;

import java.util.List;

public interface DeviceSessionService {

    DeviceSession createOrUpdate(Long userId, String deviceId, String deviceName, String deviceType, String ip, String userAgent);

    void updateLastActive(String deviceId);

    List<DeviceVO> listByUser(Long userId, String currentDeviceId);

    void deleteDevice(Long userId, String deviceId);

    void deleteAllByUser(Long userId);
}
