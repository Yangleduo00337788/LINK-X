package com.linkx.server.task.snailjob;

import com.aizuda.snailjob.client.job.core.enums.AllocationAlgorithmEnum;
import com.aizuda.snailjob.client.job.core.openapi.SnailJobOpenApi;
import com.aizuda.snailjob.common.core.enums.JobBlockStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时通过 SnailJob OpenAPI 注册集群任务（幂等：已存在则跳过）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "linkx.snail-job", name = "sync-jobs-on-startup", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SnailJobJobSyncRunner implements ApplicationRunner {

    @Value("${snail-job.group}")
    private String groupName;

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        int skipped = 0;
        for (SnailJobJobDefinition def : SnailJobJobCatalog.all()) {
            try {
                SnailJobOpenApi.addClusterJob()
                        .setJobName(def.jobName())
                        .setExecutorInfo(def.executorName())
                        .setDescription(def.description())
                        .setRouteKey(AllocationAlgorithmEnum.ROUND)
                        .setTriggerType(def.triggerType())
                        .setTriggerInterval(def.triggerInterval())
                        .setBlockStrategy(JobBlockStrategyEnum.DISCARD)
                        .setExecutorTimeout(def.executorTimeoutSeconds())
                        .setMaxRetryTimes(0)
                        .setRetryInterval(60)
                        .execute();
                created++;
                log.info("SnailJob 已注册任务: {} ({})", def.jobName(), def.executorName());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("已存在") || message.contains("exist")) {
                    skipped++;
                    log.debug("SnailJob 任务已存在，跳过: {} ({})", def.jobName(), def.executorName());
                } else {
                    log.warn("SnailJob 注册任务失败 {} ({}): {}", def.jobName(), def.executorName(), message);
                }
            }
        }
        if (created > 0 || skipped > 0) {
            log.info("SnailJob 任务同步完成：新增 {}，已存在 {}，组 {}", created, skipped, groupName);
        }
    }
}
