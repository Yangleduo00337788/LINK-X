package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
/**
 * 审核任务 SLA 超时督办（仅通知，不自动处置）。
 */
public interface ReviewEscalationService {

    /**
     * 扫描超时待审任务并记录督办。
     *
     * @return 本次督办的任务数
     */
    int processOverdueEscalations();
}
