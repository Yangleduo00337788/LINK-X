package com.linkx.server.task;

import com.linkx.server.service.admin.AdminExportJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminExportJobCleanupTask {

    private final AdminExportJobService adminExportJobService;

    @Scheduled(cron = "0 20 * * * *")
    @SchedulerLock(name = "adminExport_expireStaleJobs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireStaleJobs() {
        adminExportJobService.expireStaleJobs();
    }
}
