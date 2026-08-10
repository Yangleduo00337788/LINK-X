package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.vo.AdminSnailJobOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobTaskVO;
import com.linkx.server.controller.admin.vo.AdminSystemConnectionPoolVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorApiStatsVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorCacheVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorNamedValueVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSeriesVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorServiceVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSqlStatementVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSqlVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTaskStatsVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTrendVO;
import com.linkx.server.service.admin.AdminSnailJobMonitorService;
import com.linkx.server.service.admin.AdminSystemMonitorMetricsService;
import com.linkx.server.service.admin.MonitorSnapshotService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemMonitorMetricsServiceImpl implements AdminSystemMonitorMetricsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long SNAIL_JOB_TIMEOUT_MS = 2_000;
    private static final long REDIS_QPS_BASELINE_INTERVAL_MS = 60_000;

    /** Redis total_commands_processed 基准，用于跨请求估算 QPS */
    private final AtomicLong redisCommandsBaseline = new AtomicLong(-1);
    private final AtomicLong redisCommandsBaselineAtMs = new AtomicLong(0);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final MonitorSnapshotService snapshotService;
    private final AdminSnailJobMonitorService snailJobMonitorService;

    @Override
    public AdminMonitorCacheVO cache(int hours) {
        Properties info = loadRedisInfo();
        long usedMemory = parseLong(info.getProperty("used_memory"));
        long maxMemory = parseLong(info.getProperty("maxmemory"));
        if (maxMemory <= 0) {
            maxMemory = parseLong(info.getProperty("total_system_memory"));
        }
        long hits = parseLong(info.getProperty("keyspace_hits"));
        long misses = parseLong(info.getProperty("keyspace_misses"));
        double hitRate = hits + misses > 0 ? hits * 100.0 / (hits + misses) : 0;
        long connected = parseLong(info.getProperty("connected_clients"));
        long totalCommands = parseLong(info.getProperty("total_commands_processed"));
        long instantaneousOps = parseLong(info.getProperty("instantaneous_ops_per_sec"));
        double qps = resolveRedisQps(totalCommands, instantaneousOps);

        double memoryPct = maxMemory > 0 ? usedMemory * 100.0 / maxMemory : 0;
        double memoryUsedMb = usedMemory / 1024.0 / 1024.0;
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("memory_used", (double) usedMemory);
        metrics.put("memory_used_mb", memoryUsedMb);
        metrics.put("memory_pct", memoryPct);
        metrics.put("connected_clients", (double) connected);
        metrics.put("hit_rate", hitRate);
        metrics.put("qps", qps);
        snapshotService.recordIfDue("redis", metrics);

        Map<String, String> infoMap = new LinkedHashMap<>();
        info.stringPropertyNames().stream().sorted().limit(32).forEach(k -> infoMap.put(k, info.getProperty(k)));

        return AdminMonitorCacheVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .usedMemoryBytes(usedMemory)
                .maxMemoryBytes(maxMemory)
                .memoryUsagePercent(round1(memoryPct))
                .connectedClients(connected)
                .hitRatePercent(round1(hitRate))
                .qps(round2(qps))
                .redisVersion(info.getProperty("redis_version"))
                .info(infoMap)
                .memoryTrend(snapshotService.loadHourlyTrend("redis", "memory_used_mb", hours, memoryUsedMb))
                .qpsTrend(snapshotService.loadHourlyTrend("redis", "qps", hours, qps))
                .hitRateTrend(snapshotService.loadHourlyTrend("redis", "hit_rate", hours, hitRate))
                .connectionsTrend(snapshotService.loadHourlyTrend("redis", "connected_clients", hours, (double) connected))
                .build();
    }

    @Override
    public AdminMonitorServiceVO service(int hours) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        double heapPct = heapMax > 0 ? heapUsed * 100.0 / heapMax : 0;

        double systemCpu = -1;
        double processCpu = -1;
        long totalPhysical = 0;
        long freePhysical = 0;
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double sysLoad = sunOs.getCpuLoad();
            double procLoad = sunOs.getProcessCpuLoad();
            systemCpu = sysLoad >= 0 ? sysLoad * 100 : -1;
            processCpu = procLoad >= 0 ? procLoad * 100 : -1;
            totalPhysical = sunOs.getTotalMemorySize();
            freePhysical = sunOs.getFreeMemorySize();
        }

        File disk = new File(".").getAbsoluteFile();
        long diskTotal = disk.getTotalSpace();
        long diskFree = disk.getFreeSpace();
        double diskPct = diskTotal > 0 ? (diskTotal - diskFree) * 100.0 / diskTotal : 0;
        double sysMemPct = totalPhysical > 0 ? (totalPhysical - freePhysical) * 100.0 / totalPhysical : -1;

        Map<String, Double> metrics = new LinkedHashMap<>();
        if (systemCpu >= 0) {
            metrics.put("cpu_load", systemCpu);
        }
        metrics.put("jvm_heap_pct", heapPct);
        if (sysMemPct >= 0) {
            metrics.put("system_memory_pct", sysMemPct);
        }
        snapshotService.recordIfDue("jvm", metrics);

        String hostName = "localhost";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }

        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();

        return AdminMonitorServiceVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .osName(System.getProperty("os.name"))
                .osArch(System.getProperty("os.arch"))
                .hostName(hostName)
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                .systemCpuLoadPercent(systemCpu >= 0 ? round1(systemCpu) : 0)
                .processCpuLoadPercent(processCpu >= 0 ? round1(processCpu) : 0)
                .systemTotalMemoryBytes(totalPhysical)
                .systemFreeMemoryBytes(freePhysical)
                .systemMemoryUsagePercent(sysMemPct >= 0 ? round1(sysMemPct) : 0)
                .jvmHeapUsedBytes(heapUsed)
                .jvmHeapMaxBytes(heapMax)
                .jvmHeapUsagePercent(round1(heapPct))
                .jvmNonHeapUsedBytes(memory.getNonHeapMemoryUsage().getUsed())
                .threadCount(threads.getThreadCount())
                .peakThreadCount(threads.getPeakThreadCount())
                .gcCount(gcCount)
                .uptimeMs(ManagementFactory.getRuntimeMXBean().getUptime())
                .startTime(java.time.Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()))
                .javaVersion(System.getProperty("java.version"))
                .diskTotalBytes(diskTotal)
                .diskFreeBytes(diskFree)
                .diskUsagePercent(round1(diskPct))
                .diskPath(disk.getPath())
                .cpuTrend(snapshotService.loadHourlyTrend("jvm", "cpu_load", hours,
                        systemCpu >= 0 ? systemCpu : null))
                .memoryTrend(snapshotService.loadHourlyTrend("jvm", "jvm_heap_pct", hours, heapPct))
                .build();
    }

    @Override
    public AdminMonitorApiStatsVO apiStats(int days) {
        long total = 0;
        long success = 0;
        long failed = 0;
        Map<String, Long> byMethod = new HashMap<>();
        Map<String, Long> byUri = new HashMap<>();

        for (Timer timer : Search.in(meterRegistry).name("http.server.requests").timers()) {
            long count = (long) timer.count();
            if (count <= 0) {
                continue;
            }
            total += count;
            String status = timer.getId().getTag("status");
            if (status != null && status.startsWith("2")) {
                success += count;
            } else if (status != null && (status.startsWith("4") || status.startsWith("5"))) {
                failed += count;
            }
            String method = timer.getId().getTag("method");
            if (method != null) {
                byMethod.merge(method, count, Long::sum);
            }
            String uri = timer.getId().getTag("uri");
            if (uri != null) {
                byUri.merge(uri, count, Long::sum);
            }
        }

        snapshotService.recordIfDue("http", Map.of(
                "total_requests", (double) total,
                "success_requests", (double) success,
                "failed_requests", (double) failed));

        List<AdminMonitorNamedValueVO> methods = byMethod.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> AdminMonitorNamedValueVO.builder().key(e.getKey()).name(e.getKey()).value(e.getValue()).build())
                .toList();

        List<AdminMonitorNamedValueVO> topPaths = byUri.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> AdminMonitorNamedValueVO.builder().key(e.getKey()).name(e.getKey()).value(e.getValue()).build())
                .toList();

        return AdminMonitorApiStatsVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .totalRequests(total)
                .successRequests(success)
                .failedRequests(failed)
                .methodDistribution(methods)
                .topPaths(topPaths)
                .dailyTrend(buildHttpDailyTrend(days))
                .build();
    }

    @Override
    public AdminMonitorTaskStatsVO taskStats(int days) {
        int safeDays = Math.min(30, Math.max(1, days));
        AdminSnailJobOverviewVO overview = loadSnailJobOverview();
        List<AdminSnailJobTaskVO> tasks = overview != null ? overview.getTasks() : List.of();

        AdminMonitorTrendVO dailyTrend = loadTaskDailyTrend(safeDays);
        long[] batchCounts = countTaskBatches(safeDays);
        long successBatches = batchCounts[0];
        long failedBatches = batchCounts[1];

        long totalBatches = successBatches + failedBatches;
        double successRate = totalBatches > 0 ? successBatches * 100.0 / totalBatches : 0;

        List<AdminMonitorNamedValueVO> statusDist = new ArrayList<>();
        if (overview != null) {
            int enabled = overview.getEnabledTasks() != null ? overview.getEnabledTasks() : 0;
            int failed = overview.getFailedTasks() != null ? overview.getFailedTasks() : 0;
            int registered = overview.getRegisteredTasks() != null ? overview.getRegisteredTasks() : 0;
            statusDist.add(nv("enabled", "启用", enabled));
            statusDist.add(nv("registered", "已注册", registered));
            statusDist.add(nv("failed", "最近失败", failed));
        }

        return AdminMonitorTaskStatsVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .monitorAvailable(overview != null && Boolean.TRUE.equals(overview.getMonitorAvailable()))
                .totalTasks(overview != null && overview.getTotalTasks() != null ? overview.getTotalTasks() : tasks.size())
                .registeredTasks(overview != null && overview.getRegisteredTasks() != null ? overview.getRegisteredTasks() : 0)
                .enabledTasks(overview != null && overview.getEnabledTasks() != null ? overview.getEnabledTasks() : 0)
                .failedTasks(overview != null && overview.getFailedTasks() != null ? overview.getFailedTasks() : 0)
                .successBatches(successBatches)
                .failedBatches(failedBatches)
                .successRatePercent(round1(successRate))
                .statusDistribution(statusDist)
                .dailyTrend(dailyTrend)
                .tasks(tasks)
                .build();
    }

    @Override
    public AdminMonitorSqlVO sql(int hours, int limit) {
        int safeLimit = Math.min(50, Math.max(5, limit));
        AdminSystemConnectionPoolVO pool = loadConnectionPool();
        long questions = queryGlobalStatus("Questions");
        long slowQueries = queryGlobalStatus("Slow_queries");
        int active = pool != null && pool.getActiveConnections() != null ? pool.getActiveConnections() : 0;
        int idle = pool != null && pool.getIdleConnections() != null ? pool.getIdleConnections() : 0;
        int total = pool != null && pool.getTotalConnections() != null ? pool.getTotalConnections() : active + idle;

        snapshotService.recordIfDue("hikari", Map.of(
                "active_connections", (double) active,
                "idle_connections", (double) idle,
                "total_connections", (double) total));

        return AdminMonitorSqlVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .connectionPool(pool)
                .activeConnections(active)
                .questionsTotal(questions)
                .slowQueries(slowQueries)
                .topStatements(loadTopSqlStatements(safeLimit))
                .connectionTrend(snapshotService.loadHourlyTrend("hikari", "total_connections", hours, (double) total))
                .build();
    }

    private AdminSnailJobOverviewVO loadSnailJobOverview() {
        try {
            CompletableFuture<AdminSnailJobOverviewVO> future =
                    CompletableFuture.supplyAsync(snailJobMonitorService::overview);
            return future.get(SNAIL_JOB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.debug("SnailJob overview timed out");
            return null;
        } catch (Exception ex) {
            log.debug("SnailJob overview unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private AdminMonitorTrendVO loadTaskDailyTrend(int days) {
        List<String> labels = new ArrayList<>();
        List<Number> successData = new ArrayList<>();
        List<Number> failData = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now(ZONE).minusDays(i);
            byDay.put(d, new long[]{0, 0});
            labels.add(d.format(fmt));
        }
        long sinceEpochMs = LocalDate.now(ZONE).minusDays(days - 1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        try {
            jdbcTemplate.query(
                    """
                            SELECT DATE(FROM_UNIXTIME(b.execution_at / 1000)) AS d,
                                   SUM(CASE WHEN b.task_batch_status = 3 THEN 1 ELSE 0 END) AS success_cnt,
                                   SUM(CASE WHEN b.task_batch_status = 4 THEN 1 ELSE 0 END) AS fail_cnt
                            FROM snail_job.sj_job_task_batch b
                            WHERE b.deleted = 0
                              AND b.execution_at > 0
                              AND b.execution_at >= ?
                            GROUP BY DATE(FROM_UNIXTIME(b.execution_at / 1000))
                            """,
                    rs -> {
                        LocalDate d = rs.getDate("d").toLocalDate();
                        long[] arr = byDay.get(d);
                        if (arr != null) {
                            arr[0] = rs.getLong("success_cnt");
                            arr[1] = rs.getLong("fail_cnt");
                        }
                    },
                    sinceEpochMs);
        } catch (DataAccessException ex) {
            log.debug("Task daily trend unavailable: {}", ex.getMessage());
        }
        for (long[] arr : byDay.values()) {
            successData.add(arr[0]);
            failData.add(arr[1]);
        }
        return AdminMonitorTrendVO.builder()
                .labels(labels)
                .series(List.of(
                        AdminMonitorSeriesVO.builder().key("success").name("success").data(successData).build(),
                        AdminMonitorSeriesVO.builder().key("fail").name("fail").data(failData).build()))
                .build();
    }

    /** 近 N 日批次成功/失败次数（execution_at 为毫秒时间戳） */
    private long[] countTaskBatches(int days) {
        long sinceEpochMs = LocalDate.now(ZONE).minusDays(days - 1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        try {
            long[] counts = new long[]{0, 0};
            jdbcTemplate.query(
                    """
                            SELECT
                                COALESCE(SUM(CASE WHEN task_batch_status = 3 THEN 1 ELSE 0 END), 0) AS success_cnt,
                                COALESCE(SUM(CASE WHEN task_batch_status = 4 THEN 1 ELSE 0 END), 0) AS fail_cnt
                            FROM snail_job.sj_job_task_batch
                            WHERE deleted = 0
                              AND execution_at > 0
                              AND execution_at >= ?
                            """,
                    rs -> {
                        if (rs.next()) {
                            counts[0] = rs.getLong("success_cnt");
                            counts[1] = rs.getLong("fail_cnt");
                        }
                    },
                    sinceEpochMs);
            return counts;
        } catch (DataAccessException ex) {
            log.debug("Task batch count unavailable: {}", ex.getMessage());
            return new long[]{0, 0};
        }
    }

    private AdminMonitorTrendVO buildHttpDailyTrend(int days) {
        AdminMonitorTrendVO fromSnapshot = snapshotService.loadDailyTrend("http", "total_requests", days);
        if (fromSnapshot.getLabels() != null && !fromSnapshot.getLabels().isEmpty()
                && fromSnapshot.getSeries() != null && !fromSnapshot.getSeries().isEmpty()
                && fromSnapshot.getSeries().get(0).getData().stream().anyMatch(n -> n.doubleValue() > 0)) {
            return fromSnapshot;
        }
        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        long todayTotal = 0;
        for (Timer timer : Search.in(meterRegistry).name("http.server.requests").timers()) {
            todayTotal += (long) timer.count();
        }
        for (int i = days - 1; i >= 0; i--) {
            labels.add(LocalDate.now(ZONE).minusDays(i).format(fmt));
            data.add(i == days - 1 ? todayTotal : 0);
        }
        return AdminMonitorTrendVO.builder()
                .labels(labels)
                .series(List.of(AdminMonitorSeriesVO.builder().key("requests").name("requests").data(data).build()))
                .build();
    }

    private List<AdminMonitorSqlStatementVO> loadTopSqlStatements(int limit) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT DIGEST_TEXT AS sample_sql,
                                   COUNT_STAR AS exec_count,
                                   ROUND(AVG_TIMER_WAIT / 1000000, 2) AS avg_ms,
                                   ROUND(SUM_TIMER_WAIT / 1000000, 2) AS total_ms
                            FROM performance_schema.events_statements_summary_by_digest
                            WHERE SCHEMA_NAME = DATABASE()
                            ORDER BY SUM_TIMER_WAIT DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> AdminMonitorSqlStatementVO.builder()
                            .digest("digest-" + rowNum)
                            .sampleSql(truncate(rs.getString("sample_sql"), 200))
                            .execCount(rs.getLong("exec_count"))
                            .avgLatencyMs(rs.getDouble("avg_ms"))
                            .totalLatencyMs(rs.getDouble("total_ms"))
                            .build(),
                    limit);
        } catch (DataAccessException ex) {
            log.debug("performance_schema unavailable: {}", ex.getMessage());
            return List.of();
        }
    }

    private long queryGlobalStatus(String name) {
        try {
            Long val = jdbcTemplate.queryForObject("SHOW GLOBAL STATUS LIKE ?", (rs, rowNum) -> rs.getLong(2), name);
            return val != null ? val : 0;
        } catch (DataAccessException ex) {
            try {
                Long val = jdbcTemplate.queryForObject("SHOW STATUS LIKE ?", (rs, rowNum) -> rs.getLong(2), name);
                return val != null ? val : 0;
            } catch (DataAccessException ignored) {
                return 0;
            }
        }
    }

    private AdminSystemConnectionPoolVO loadConnectionPool() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return null;
        }
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return AdminSystemConnectionPoolVO.builder()
                    .poolName(hikari.getPoolName())
                    .maxConnections(hikari.getMaximumPoolSize())
                    .build();
        }
        return AdminSystemConnectionPoolVO.builder()
                .poolName(hikari.getPoolName())
                .activeConnections(pool.getActiveConnections())
                .idleConnections(pool.getIdleConnections())
                .totalConnections(pool.getTotalConnections())
                .maxConnections(hikari.getMaximumPoolSize())
                .threadsAwaitingConnection(pool.getThreadsAwaitingConnection())
                .build();
    }

    /**
     * Redis QPS：优先用两次采样间命令增量 / 实际间隔；无增量时回退 Redis instantaneous_ops_per_sec。
     */
    private double resolveRedisQps(long totalCommands, long instantaneousOps) {
        long nowMs = System.currentTimeMillis();
        if (redisCommandsBaseline.get() < 0) {
            loadRedisCommandsBaselineFromDb();
        }

        long baseline = redisCommandsBaseline.get();
        long baselineAt = redisCommandsBaselineAtMs.get();
        double qps = instantaneousOps > 0 ? instantaneousOps : 0;

        if (baseline >= 0 && baselineAt > 0) {
            long elapsedMs = nowMs - baselineAt;
            if (elapsedMs > REDIS_QPS_BASELINE_INTERVAL_MS * 5) {
                redisCommandsBaseline.set(totalCommands);
                redisCommandsBaselineAtMs.set(nowMs);
                persistRedisCommandsBaseline(totalCommands);
                return round2(instantaneousOps > 0 ? instantaneousOps : 0);
            }
            elapsedMs = Math.max(1000, elapsedMs);
            if (totalCommands >= baseline) {
                long delta = totalCommands - baseline;
                if (delta > 0) {
                    qps = delta * 1000.0 / elapsedMs;
                }
            }
        }

        if (baseline < 0 || nowMs - baselineAt >= REDIS_QPS_BASELINE_INTERVAL_MS) {
            redisCommandsBaseline.set(totalCommands);
            redisCommandsBaselineAtMs.set(nowMs);
            persistRedisCommandsBaseline(totalCommands);
            if (baseline < 0) {
                return round2(instantaneousOps > 0 ? instantaneousOps : 0);
            }
        }

        return round2(qps);
    }

    private void loadRedisCommandsBaselineFromDb() {
        try {
            jdbcTemplate.query(
                    """
                            SELECT metric_value, snapshot_at
                            FROM sys_monitor_metric_snapshot
                            WHERE category = ? AND metric_key = ?
                            ORDER BY snapshot_at DESC
                            LIMIT 1
                            """,
                    rs -> {
                        if (rs.next()) {
                            redisCommandsBaseline.set((long) rs.getDouble("metric_value"));
                            Timestamp ts = rs.getTimestamp("snapshot_at");
                            if (ts != null) {
                                redisCommandsBaselineAtMs.set(ts.toInstant().toEpochMilli());
                            }
                        }
                    },
                    "redis", "total_commands");
        } catch (DataAccessException ex) {
            log.debug("Redis QPS baseline unavailable: {}", ex.getMessage());
        }
    }

    private void persistRedisCommandsBaseline(long totalCommands) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO sys_monitor_metric_snapshot
                            (snapshot_at, category, metric_key, metric_value)
                            VALUES (?, ?, ?, ?)
                            """,
                    LocalDateTime.now(ZONE), "redis", "total_commands", (double) totalCommands);
        } catch (DataAccessException ex) {
            log.debug("Redis QPS baseline persist skipped: {}", ex.getMessage());
        }
    }

    private Properties loadRedisInfo() {
        try (var connection = redisConnectionFactory.getConnection()) {
            Properties props = connection.serverCommands().info();
            return props != null ? props : new Properties();
        } catch (RuntimeException ex) {
            log.debug("Redis info failed: {}", ex.getMessage());
            return new Properties();
        }
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static AdminMonitorNamedValueVO nv(String key, String name, long value) {
        return AdminMonitorNamedValueVO.builder().key(key).name(name).value(value).build();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
