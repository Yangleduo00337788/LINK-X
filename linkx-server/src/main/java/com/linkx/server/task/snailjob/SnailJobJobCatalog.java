package com.linkx.server.task.snailjob;

import com.aizuda.snailjob.client.job.core.enums.TriggerTypeEnum;

import java.util.List;

/**
 * LinkX 内置定时任务目录，与历史 {@code ScheduledTaskCatalog} 调度语义对齐。
 */
public final class SnailJobJobCatalog {

    private SnailJobJobCatalog() {
    }

    public static List<SnailJobJobDefinition> all() {
        return List.of(
                cron("feedback_escalation", "反馈超时升级",
                        "扫描超过 SLA 的待处理反馈，自动分流/改派并记录升级",
                        "0 15 * * * ?", 600),
                cron("review_escalation", "审核超时督办",
                        "扫描超过 SLA 的待审任务并推送管理端督办通知",
                        "0 45 * * * ?", 600),
                cron("red_packet_expire", "红包过期退款",
                        "检查过期红包并自动退款",
                        "0 * * * * ?", 120),
                cron("message_retention", "消息留存清理",
                        "逻辑删除超过保留期的历史消息并清理附件",
                        "0 0 3 * * ?", 1800),
                cron("group_mute", "群禁言调度",
                        "应用/结束定时全体禁言，清理到期成员禁言",
                        "0 * * * * ?", 120),
                cron("admin_export_cleanup", "导出任务清理",
                        "过期管理端异步导出任务标记失败",
                        "0 20 * * * ?", 600),
                fixed("auto_unlock", "登录失败自动解封",
                        "封禁到期后恢复账号为启用状态",
                        60, 120),
                fixed("presence_heartbeat", "在线心跳清扫",
                        "刷新实例心跳并清扫宕机实例（影响在线状态，慎改频率）",
                        5, 60),
                fixed("sensitive_word_refresh", "敏感词库刷新",
                        "从数据库重新加载敏感词 DFA",
                        300, 300)
        );
    }

    private static SnailJobJobDefinition cron(String executorName, String jobName, String description,
                                              String cron, int timeoutSeconds) {
        return new SnailJobJobDefinition(
                executorName, jobName, description, TriggerTypeEnum.CRON, cron, timeoutSeconds);
    }

    private static SnailJobJobDefinition fixed(String executorName, String jobName, String description,
                                               int intervalSeconds, int timeoutSeconds) {
        return new SnailJobJobDefinition(
                executorName, jobName, description, TriggerTypeEnum.SCHEDULED_TIME,
                String.valueOf(intervalSeconds), timeoutSeconds);
    }
}
