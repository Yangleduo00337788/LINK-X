package com.linkx.server.service.admin.impl;

import com.linkx.server.controller.admin.vo.AdminSnailJobOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSystemBusinessMetricsVO;
import com.linkx.server.controller.admin.vo.AdminSystemConnectionPoolVO;
import com.linkx.server.controller.admin.vo.AdminSystemDependencyVO;
import com.linkx.server.controller.admin.vo.AdminSystemHttpMetricsVO;
import com.linkx.server.controller.admin.vo.AdminSystemMonitorOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSystemRuntimeVO;
import com.linkx.server.controller.admin.vo.AdminSystemScheduledTaskSummaryVO;
import com.linkx.server.controller.admin.vo.AdminSystemStorageSummaryVO;
import com.linkx.server.controller.admin.vo.AdminSystemTableStatVO;
import com.linkx.server.controller.admin.vo.AdminSystemTableStatsVO;
import com.linkx.server.service.admin.AdminSnailJobMonitorService;
import com.linkx.server.service.admin.AdminSystemMonitorService;
import com.linkx.server.task.snailjob.SnailJobJobCatalog;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemMonitorServiceImpl implements AdminSystemMonitorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long TABLE_CACHE_MS = 300_000;
    private static final long SNAIL_JOB_CACHE_MS = 60_000;
    private static final long SNAIL_JOB_TIMEOUT_MS = 2_000;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MeterRegistry meterRegistry;
    private final Environment environment;
    private final AdminSnailJobMonitorService snailJobMonitorService;

    private volatile List<AdminSystemTableStatVO> cachedTables;
    private volatile String cachedTablesSchema;
    private volatile long cachedTablesAt;
    private volatile AdminSystemScheduledTaskSummaryVO cachedScheduledTasks;
    private volatile long cachedScheduledTasksAt;

    @Override
    public AdminSystemMonitorOverviewVO overview() {
        String schemaName = resolveSchemaName();
        return AdminSystemMonitorOverviewVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .applicationName(environment.getProperty("spring.application.name", "linkx-server"))
                .activeProfile(String.join(",", environment.getActiveProfiles()))
                .schemaName(schemaName)
                .runtime(loadRuntime())
                .dependencies(loadDependencies())
                .connectionPool(loadConnectionPool())
                .http(loadHttpMetrics())
                .business(loadBusinessMetrics())
                .scheduledTasks(loadScheduledTaskSummary())
                .storage(null)
                .tables(List.of())
                .rowCountApproximate(true)
                .build();
    }

    @Override
    public AdminSystemTableStatsVO tableStats(boolean refresh) {
        String schemaName = resolveSchemaName();
        long now = System.currentTimeMillis();
        boolean fromCache = !refresh
                && cachedTables != null
                && schemaName.equals(cachedTablesSchema)
                && now - cachedTablesAt < TABLE_CACHE_MS;
        List<AdminSystemTableStatVO> tables = fromCache
                ? cachedTables
                : loadTableStats(schemaName, refresh);
        AdminSystemStorageSummaryVO storage = summarizeStorage(tables);
        return AdminSystemTableStatsVO.builder()
                .refreshedAt(LocalDateTime.now(ZONE))
                .schemaName(schemaName)
                .storage(storage)
                .tableList(tables)
                .rowCountApproximate(true)
                .cached(fromCache)
                .build();
    }

    private String resolveSchemaName() {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog != null && !catalog.isBlank()) {
                return catalog.trim();
            }
        } catch (SQLException ex) {
            log.debug("connection catalog unavailable: {}", ex.getMessage());
        }

        String fromConfig = schemaFromJdbcUrl(environment.getProperty("spring.datasource.url"));
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig;
        }

        try {
            String schema = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(DATABASE(), SCHEMA())", String.class);
            if (schema != null && !schema.isBlank()) {
                return schema.trim();
            }
        } catch (DataAccessException ex) {
            log.warn("Failed to resolve database schema via SQL: {}", ex.getMessage());
        }
        return "unknown";
    }

    private static String schemaFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        String normalized = jdbcUrl.trim();
        int scheme = normalized.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int slash = normalized.indexOf('/', scheme + 3);
        if (slash < 0 || slash + 1 >= normalized.length()) {
            return null;
        }
        String dbPart = normalized.substring(slash + 1);
        int query = dbPart.indexOf('?');
        if (query >= 0) {
            dbPart = dbPart.substring(0, query);
        }
        int semicolon = dbPart.indexOf(';');
        if (semicolon >= 0) {
            dbPart = dbPart.substring(0, semicolon);
        }
        dbPart = dbPart.trim();
        return dbPart.isEmpty() ? null : dbPart;
    }

    private AdminSystemRuntimeVO loadRuntime() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        double heapPercent = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0.0;
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();

        return AdminSystemRuntimeVO.builder()
                .uptimeMs(ManagementFactory.getRuntimeMXBean().getUptime())
                .startTime(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()))
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .osArch(System.getProperty("os.arch"))
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                .heapUsedBytes(heapUsed)
                .heapMaxBytes(heapMax)
                .heapUsagePercent(Math.round(heapPercent * 10.0) / 10.0)
                .nonHeapUsedBytes(memory.getNonHeapMemoryUsage().getUsed())
                .threadCount(threads.getThreadCount())
                .peakThreadCount(threads.getPeakThreadCount())
                .gcCount(gcCount)
                .build();
    }

    private List<AdminSystemDependencyVO> loadDependencies() {
        List<AdminSystemDependencyVO> dependencies = new ArrayList<>(3);
        dependencies.add(measureDatabase());
        dependencies.add(measureRedis());
        dependencies.add(measureDisk());
        return dependencies;
    }

    private AdminSystemDependencyVO measureDatabase() {
        long start = System.nanoTime();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Map<String, Object> details = new LinkedHashMap<>();
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData meta = connection.getMetaData();
                details.put("database", meta.getDatabaseProductName());
                details.put("version", meta.getDatabaseProductVersion());
            }
            return AdminSystemDependencyVO.builder()
                    .name("db")
                    .status(Status.UP.getCode())
                    .latencyMs(latencyMs)
                    .details(details)
                    .build();
        } catch (Exception ex) {
            return AdminSystemDependencyVO.builder()
                    .name("db")
                    .status(Status.DOWN.getCode())
                    .details(Map.of("error", ex.getMessage()))
                    .build();
        }
    }

    private AdminSystemDependencyVO measureRedis() {
        long start = System.nanoTime();
        try {
            String pong;
            try (var connection = redisConnectionFactory.getConnection()) {
                pong = connection.ping();
            }
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return AdminSystemDependencyVO.builder()
                    .name("redis")
                    .status("PONG".equalsIgnoreCase(pong) ? Status.UP.getCode() : Status.DOWN.getCode())
                    .latencyMs(latencyMs)
                    .details(Map.of("response", pong != null ? pong : ""))
                    .build();
        } catch (RuntimeException ex) {
            return AdminSystemDependencyVO.builder()
                    .name("redis")
                    .status(Status.DOWN.getCode())
                    .details(Map.of("error", ex.getMessage()))
                    .build();
        }
    }

    private AdminSystemDependencyVO measureDisk() {
        try {
            File root = new File(".").getAbsoluteFile();
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            boolean healthy = total <= 0 || free > 0;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("path", root.getPath());
            details.put("total", total);
            details.put("free", free);
            return AdminSystemDependencyVO.builder()
                    .name("diskSpace")
                    .status(healthy ? Status.UP.getCode() : Status.DOWN.getCode())
                    .details(details)
                    .build();
        } catch (RuntimeException ex) {
            return AdminSystemDependencyVO.builder()
                    .name("diskSpace")
                    .status(Status.UNKNOWN.getCode())
                    .details(Map.of("error", ex.getMessage()))
                    .build();
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

    private AdminSystemHttpMetricsVO loadHttpMetrics() {
        long total = 0;
        long clientErrors = 0;
        long serverErrors = 0;
        double totalTimeMs = 0;

        for (Timer timer : Search.in(meterRegistry).name("http.server.requests").timers()) {
            long count = (long) timer.count();
            if (count <= 0) {
                continue;
            }
            total += count;
            totalTimeMs += timer.totalTime(TimeUnit.MILLISECONDS);
            String status = timer.getId().getTag("status");
            if (status != null) {
                if (status.startsWith("4")) {
                    clientErrors += count;
                } else if (status.startsWith("5")) {
                    serverErrors += count;
                }
            }
        }

        double avgLatency = total > 0 ? totalTimeMs / total : 0;
        return AdminSystemHttpMetricsVO.builder()
                .totalRequests(total)
                .clientErrorRequests(clientErrors)
                .serverErrorRequests(serverErrors)
                .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                .p95LatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                .build();
    }

    private AdminSystemBusinessMetricsVO loadBusinessMetrics() {
        return AdminSystemBusinessMetricsVO.builder()
                .loginSuccess(counterValue("linkx.auth.login.success"))
                .loginFailure(counterValue("linkx.auth.login.failure"))
                .registerSuccess(counterValue("linkx.auth.register.success"))
                .registerFailure(counterValue("linkx.auth.register.failure"))
                .messageSent(counterValue("linkx.message.sent"))
                .fileUploadSuccess(counterValue("linkx.file.upload.success"))
                .fileUploadFailure(counterValue("linkx.file.upload.failure"))
                .tokenRefreshSuccess(counterValue("linkx.token.refresh.success"))
                .tokenRefreshFailure(counterValue("linkx.token.refresh.failure"))
                .build();
    }

    private long counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? (long) counter.count() : 0;
    }

    private AdminSystemScheduledTaskSummaryVO loadScheduledTaskSummary() {
        long now = System.currentTimeMillis();
        AdminSystemScheduledTaskSummaryVO cached = cachedScheduledTasks;
        if (cached != null && now - cachedScheduledTasksAt < SNAIL_JOB_CACHE_MS) {
            return cached;
        }

        int catalogSize = SnailJobJobCatalog.all().size();
        AdminSystemScheduledTaskSummaryVO fallback = AdminSystemScheduledTaskSummaryVO.builder()
                .monitorAvailable(false)
                .totalTasks(catalogSize)
                .registeredTasks(0)
                .enabledTasks(0)
                .failedTasks(0)
                .build();

        CompletableFuture<AdminSnailJobOverviewVO> future =
                CompletableFuture.supplyAsync(snailJobMonitorService::overview);
        try {
            AdminSnailJobOverviewVO snailJob = future.get(SNAIL_JOB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            AdminSystemScheduledTaskSummaryVO summary = AdminSystemScheduledTaskSummaryVO.builder()
                    .monitorAvailable(Boolean.TRUE.equals(snailJob.getMonitorAvailable()))
                    .totalTasks(snailJob.getTotalTasks() != null ? snailJob.getTotalTasks() : catalogSize)
                    .registeredTasks(snailJob.getRegisteredTasks() != null ? snailJob.getRegisteredTasks() : 0)
                    .enabledTasks(snailJob.getEnabledTasks() != null ? snailJob.getEnabledTasks() : 0)
                    .failedTasks(snailJob.getFailedTasks() != null ? snailJob.getFailedTasks() : 0)
                    .build();
            cachedScheduledTasks = summary;
            cachedScheduledTasksAt = now;
            return summary;
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.debug("SnailJob summary timed out after {}ms", SNAIL_JOB_TIMEOUT_MS);
            return fallback;
        } catch (Exception ex) {
            future.cancel(true);
            log.debug("SnailJob summary unavailable: {}", ex.getMessage());
            return fallback;
        }
    }

    private List<AdminSystemTableStatVO> loadTableStats(String schemaName, boolean refresh) {
        if (schemaName == null || "unknown".equals(schemaName)) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        if (!refresh
                && cachedTables != null
                && schemaName.equals(cachedTablesSchema)
                && now - cachedTablesAt < TABLE_CACHE_MS) {
            return cachedTables;
        }
        try {
            List<AdminSystemTableStatVO> tables = isH2Database()
                    ? loadTableStatsH2(schemaName)
                    : loadTableStatsMySql(schemaName);
            cachedTables = tables;
            cachedTablesSchema = schemaName;
            cachedTablesAt = now;
            return tables;
        } catch (DataAccessException ex) {
            log.warn("Failed to load information_schema tables: {}", ex.getMessage());
            return cachedTables != null ? cachedTables : List.of();
        }
    }

    private boolean isH2Database() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName();
            return product != null && product.toLowerCase().contains("h2");
        } catch (Exception ex) {
            return false;
        }
    }

    private List<AdminSystemTableStatVO> loadTableStatsMySql(String schemaName) {
        return jdbcTemplate.query(
                """
                        SELECT TABLE_NAME,
                               ENGINE,
                               IFNULL(TABLE_ROWS, 0) AS TABLE_ROWS,
                               IFNULL(DATA_LENGTH, 0) AS DATA_LENGTH,
                               IFNULL(INDEX_LENGTH, 0) AS INDEX_LENGTH,
                               TABLE_COMMENT,
                               CREATE_TIME,
                               UPDATE_TIME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = ?
                          AND TABLE_TYPE = 'BASE TABLE'
                        ORDER BY (IFNULL(DATA_LENGTH, 0) + IFNULL(INDEX_LENGTH, 0)) DESC,
                                 TABLE_NAME ASC
                        """,
                (rs, rowNum) -> mapTableStat(rs),
                schemaName);
    }

    private List<AdminSystemTableStatVO> loadTableStatsH2(String schemaName) {
        return jdbcTemplate.query(
                """
                        SELECT TABLE_NAME,
                               CAST(NULL AS VARCHAR(32)) AS ENGINE,
                               CAST(0 AS BIGINT) AS TABLE_ROWS,
                               CAST(0 AS BIGINT) AS DATA_LENGTH,
                               CAST(0 AS BIGINT) AS INDEX_LENGTH,
                               REMARKS AS TABLE_COMMENT,
                               CAST(NULL AS TIMESTAMP) AS CREATE_TIME,
                               CAST(NULL AS TIMESTAMP) AS UPDATE_TIME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = ?
                          AND TABLE_TYPE = 'BASE TABLE'
                        ORDER BY TABLE_NAME ASC
                        """,
                (rs, rowNum) -> mapTableStat(rs),
                schemaName);
    }

    private AdminSystemTableStatVO mapTableStat(ResultSet rs) throws SQLException {
        long dataBytes = rs.getLong("DATA_LENGTH");
        long indexBytes = rs.getLong("INDEX_LENGTH");
        return AdminSystemTableStatVO.builder()
                .tableName(rs.getString("TABLE_NAME"))
                .engine(rs.getString("ENGINE"))
                .rowCount(rs.getLong("TABLE_ROWS"))
                .dataBytes(dataBytes)
                .indexBytes(indexBytes)
                .totalBytes(dataBytes + indexBytes)
                .tableComment(rs.getString("TABLE_COMMENT"))
                .createTime(toLocalDateTime(rs.getTimestamp("CREATE_TIME")))
                .updateTime(toLocalDateTime(rs.getTimestamp("UPDATE_TIME")))
                .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant().atZone(ZONE).toLocalDateTime();
    }

    private AdminSystemStorageSummaryVO summarizeStorage(List<AdminSystemTableStatVO> tables) {
        long rows = 0;
        long dataBytes = 0;
        long indexBytes = 0;
        for (AdminSystemTableStatVO table : tables) {
            rows += table.getRowCount();
            dataBytes += table.getDataBytes();
            indexBytes += table.getIndexBytes();
        }
        return AdminSystemStorageSummaryVO.builder()
                .tableCount(tables.size())
                .approximateRowCount(rows)
                .dataBytes(dataBytes)
                .indexBytes(indexBytes)
                .totalBytes(dataBytes + indexBytes)
                .build();
    }
}
