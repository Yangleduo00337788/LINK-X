package com.linkx.server.service.admin;

/**
 * 反馈 SLA 超时升级与改派。
 */
public interface FeedbackEscalationService {

    /**
     * 扫描超时待处理反馈并执行升级。
     *
     * @return 本次处理的工单数
     */
    int processOverdueEscalations();
}
