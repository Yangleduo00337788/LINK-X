package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.admin.AdminSystemMonitorMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台每分钟采样系统监控指标，供趋势图使用（不依赖管理员打开监控页）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorSnapshotScheduler {

    private final AdminSystemMonitorMetricsService metricsService;

    @Scheduled(fixedRate = 60_000, initialDelay = 45_000)
    public void collectSnapshots() {
        try {
            metricsService.cache(1);
            metricsService.service(1);
            metricsService.apiStats(1);
            metricsService.sql(1, 5);
        } catch (Exception ex) {
            log.debug("Monitor snapshot scheduler skipped: {}", ex.getMessage());
        }
    }
}
