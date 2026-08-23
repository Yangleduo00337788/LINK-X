package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.admin.StatisticSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticSnapshotTask {

    private final StatisticSnapshotService statisticSnapshotService;

    public void captureDailySnapshots() {
        int rows = statisticSnapshotService.captureYesterdaySnapshots();
        log.info("Captured {} admin statistic/dashboard snapshots for yesterday", rows);
    }

    public void backfillSnapshots(int days) {
        int rows = statisticSnapshotService.backfillSnapshots(days);
        log.info("Backfilled {} admin statistic snapshot rows for last {} days", rows, days);
    }
}
