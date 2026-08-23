package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import java.time.LocalDate;
import java.util.Map;

/**
 * 读取 {@code sys_admin_statistic_snapshot} 中已落库的日粒度指标与热力图桶。
 */
public interface AdminStatisticSnapshotQueryService {

    /**
     * 加载指定指标在日期区间内的快照值（含起止日）。
     *
     * @param metricKey 与 {@link StatisticSnapshotService} 写入的 metric_key 一致
     * @param start     起始日期（含）
     * @param end       结束日期（含）
     * @return 日期 → 指标值；无快照的日期不会出现在 map 中
     */
    Map<LocalDate, Long> loadDailyMetrics(String metricKey, LocalDate start, LocalDate end);

    /**
     * 加载指定热力图指标在日期区间内的快照桶（按星期×小时累加）。
     *
     * @param metricKey {@code messages} 或 {@code logins}
     */
    long[][] loadHeatmap(String metricKey, LocalDate start, LocalDate end);
}
