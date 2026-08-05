package com.linkx.server.service.admin;

public interface AdminAccessRiskService {

    /** 登录前（匿名）评估：IP 失败次数等 */
    AdminAccessRiskAssessment evaluatePreLogin(String ip, String deviceId);

    /** 登录成功后评估：新 IP、新设备等 */
    AdminAccessRiskAssessment evaluatePostLogin(Long userId, String ip, String deviceId, boolean newLoginIp);

    void recordLoginFailure(String ip);

    void clearLoginFailures(String ip);
}
