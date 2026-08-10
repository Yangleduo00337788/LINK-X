package com.linkx.server.task.snailjob;


/**
 * 作者：yangleduo
 */
import com.aizuda.snailjob.client.job.core.enums.TriggerTypeEnum;

/**
 * LinkX 内置 SnailJob 任务定义（执行器名与 {@link com.aizuda.snailjob.client.job.core.annotation.JobExecutor#name()} 一致）。
 */
public record SnailJobJobDefinition(
        String executorName,
        String jobName,
        String description,
        TriggerTypeEnum triggerType,
        String triggerInterval,
        int executorTimeoutSeconds) {
}
