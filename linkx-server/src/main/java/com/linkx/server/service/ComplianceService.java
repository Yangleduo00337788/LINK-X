package com.linkx.server.service;

import com.linkx.server.controller.vo.UserDataExportVO;

/**
 * 数据合规：导出、清除、审计。
 */
public interface ComplianceService {

    /** 导出当前用户的个人数据快照 */
    UserDataExportVO exportUserData(Long userId);

    /** 清除用户业务数据（注销配套），保留最小审计痕迹 */
    void purgeUserData(Long userId);

    /** 记录合规相关审计 */
    void audit(Long userId, String action, String detail, boolean success);
}
