package com.linkx.server.service;

/**
 * 设备长期封禁与强绑定校验。
 */
public interface DeviceSecurityService {

    /** 登录前校验：封禁 / 强绑定未批准则抛业务异常 */
    void assertDeviceAllowed(Long userId, String deviceId);

    boolean isBanned(Long userId, String deviceId);

    boolean isBindingEnabled(Long userId);

    boolean isApproved(Long userId, String deviceId);
}
