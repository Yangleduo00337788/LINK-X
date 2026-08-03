package com.linkx.server.task;

import com.linkx.server.service.admin.AdminExportJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminExportJobCleanupTask {

    private final AdminExportJobService adminExportJobService;

    public void expireStaleJobs() {
        adminExportJobService.expireStaleJobs();
    }
}
