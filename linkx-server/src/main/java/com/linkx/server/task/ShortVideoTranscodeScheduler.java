package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未部署 SnailJob 时，用 Spring 定时任务处理短视频转码（本地开发友好）。
 * 生产环境建议 {@code snail-job.enabled=true}，由 SnailJob 统一调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "linkx.short-video", name = "transcode-enabled", havingValue = "true")
@ConditionalOnProperty(name = "snail-job.enabled", havingValue = "false", matchIfMissing = true)
public class ShortVideoTranscodeScheduler {

    private final ShortVideoTranscodeTask shortVideoTranscodeTask;

    @Scheduled(fixedRate = 120_000, initialDelay = 60_000)
    public void processPending() {
        try {
            int processed = shortVideoTranscodeTask.processPending();
            if (processed > 0) {
                log.info("短视频转码（本地调度）本批处理 {} 条", processed);
            }
        } catch (Exception ex) {
            log.warn("短视频转码调度失败: {}", ex.getMessage());
        }
    }
}
