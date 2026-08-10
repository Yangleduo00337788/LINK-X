package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobBatchVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobLogVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobTaskVO;
import com.linkx.server.service.admin.AdminSnailJobMonitorService;
import com.linkx.server.task.snailjob.SnailJobJobCatalog;
import com.linkx.server.task.snailjob.SnailJobJobDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSnailJobMonitorServiceImpl implements AdminSnailJobMonitorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    @Value("${linkx.snail-job.admin-console-url}")
    private String adminConsoleUrl;

    @Value("${snail-job.group}")
    private String clientGroup;

    @Value("${snail-job.namespace}")
    private String namespaceId;

    @Override
    public AdminSnailJobOverviewVO overview() {
        LocalDateTime refreshedAt = LocalDateTime.now(ZONE);
        Map<String, JobRuntimeRow> runtimeByExecutor = loadRuntimeByExecutor();
        boolean monitorAvailable = runtimeByExecutor != null;
        List<AdminSnailJobTaskVO> tasks = SnailJobJobCatalog.all().stream()
                .map(def -> toTaskVo(def, monitorAvailable ? runtimeByExecutor.get(def.executorName()) : null))
                .toList();
        int registered = (int) tasks.stream().filter(t -> Boolean.TRUE.equals(t.getRegistered())).count();
        int enabled = (int) tasks.stream().filter(t -> t.getJobStatus() != null && t.getJobStatus() == 1).count();
        int failed = (int) tasks.stream().filter(t -> "FAIL".equals(t.getLastBatchStatus())).count();
        return AdminSnailJobOverviewVO.builder()
                .adminConsoleUrl(adminConsoleUrl)
                .clientGroup(clientGroup)
                .tasks(tasks)
                .monitorAvailable(monitorAvailable)
                .refreshedAt(refreshedAt)
                .totalTasks(tasks.size())
                .registeredTasks(registered)
                .enabledTasks(enabled)
                .failedTasks(failed)
                .build();
    }

    @Override
    public PageResultVO<AdminSnailJobBatchVO> listBatches(Long jobId, long page, long size) {
        if (!isMonitorAvailable()) {
            return PageResultVO.empty(page, size);
        }
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        long offset = (safePage - 1) * safeSize;
        try {
            Long total = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(1)
                            FROM snail_job.sj_job_task_batch
                            WHERE job_id = ? AND deleted = 0
                            """,
                    Long.class,
                    jobId);
            if (total == null || total == 0) {
                return PageResultVO.empty(safePage, safeSize);
            }
            List<AdminSnailJobBatchVO> items = jdbcTemplate.query(
                    """
                            SELECT b.id, b.job_id, j.job_name, b.task_batch_status, b.execution_at, b.create_dt,
                                   b.operation_reason,
                                   TIMESTAMPDIFF(MICROSECOND, t.create_dt, t.update_dt) / 1000 AS duration_ms
                            FROM snail_job.sj_job_task_batch b
                            JOIN snail_job.sj_job j ON j.id = b.job_id
                            LEFT JOIN snail_job.sj_job_task t ON t.task_batch_id = b.id
                            WHERE b.job_id = ? AND b.deleted = 0
                            ORDER BY b.id DESC
                            LIMIT ? OFFSET ?
                            """,
                    (rs, rowNum) -> mapBatch(rs),
                    jobId, safeSize, offset);
            return PageResultVO.of(items, safePage, safeSize, total);
        } catch (DataAccessException e) {
            log.debug("SnailJob 批次查询失败: {}", e.getMessage());
            return PageResultVO.empty(safePage, safeSize);
        }
    }

    @Override
    public PageResultVO<AdminSnailJobLogVO> listLogs(Long batchId, long page, long size) {
        if (!isMonitorAvailable()) {
            return PageResultVO.empty(page, size);
        }
        long safePage = Math.max(1, page);
        long safeSize = Math.min(200, Math.max(1, size));
        long offset = (safePage - 1) * safeSize;
        try {
            Long total = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(1)
                            FROM snail_job.sj_job_log_message
                            WHERE task_batch_id = ?
                            """,
                    Long.class,
                    batchId);
            if (total == null || total == 0) {
                return PageResultVO.empty(safePage, safeSize);
            }
            List<AdminSnailJobLogVO> items = jdbcTemplate.query(
                    """
                            SELECT id, task_batch_id, task_id, message, log_num, create_dt
                            FROM snail_job.sj_job_log_message
                            WHERE task_batch_id = ?
                            ORDER BY id DESC
                            LIMIT ? OFFSET ?
                            """,
                    (rs, rowNum) -> AdminSnailJobLogVO.builder()
                            .id(rs.getLong("id"))
                            .taskBatchId(rs.getLong("task_batch_id"))
                            .taskId(rs.getLong("task_id"))
                            .message(rs.getString("message"))
                            .logNum(rs.getInt("log_num"))
                            .createDt(toLocalDateTime(rs.getTimestamp("create_dt")))
                            .build(),
                    batchId, safeSize, offset);
            return PageResultVO.of(items, safePage, safeSize, total);
        } catch (DataAccessException e) {
            log.debug("SnailJob 日志查询失败: {}", e.getMessage());
            return PageResultVO.empty(safePage, safeSize);
        }
    }

    private Map<String, JobRuntimeRow> loadRuntimeByExecutor() {
        try {
            List<JobRuntimeRow> rows = jdbcTemplate.query(
                    """
                            SELECT j.id, j.executor_info, j.job_status, j.next_trigger_at,
                                   b.id AS last_batch_id, b.task_batch_status, b.execution_at,
                                   TIMESTAMPDIFF(MICROSECOND, t.create_dt, t.update_dt) / 1000 AS duration_ms
                            FROM snail_job.sj_job j
                            LEFT JOIN (
                                SELECT job_id, MAX(id) AS max_id
                                FROM snail_job.sj_job_task_batch
                                WHERE deleted = 0
                                GROUP BY job_id
                            ) lb ON lb.job_id = j.id
                            LEFT JOIN snail_job.sj_job_task_batch b ON b.id = lb.max_id
                            LEFT JOIN snail_job.sj_job_task t ON t.task_batch_id = b.id
                            WHERE j.namespace_id = ? AND j.group_name = ? AND j.deleted = 0
                            """,
                    (rs, rowNum) -> new JobRuntimeRow(
                            rs.getLong("id"),
                            rs.getString("executor_info"),
                            rs.getInt("job_status"),
                            rs.getLong("next_trigger_at"),
                            rs.getObject("last_batch_id") != null ? rs.getLong("last_batch_id") : null,
                            rs.getObject("task_batch_status") != null ? rs.getInt("task_batch_status") : null,
                            rs.getLong("execution_at"),
                            rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null
                    ),
                    namespaceId, clientGroup);
            Map<String, JobRuntimeRow> map = new HashMap<>();
            for (JobRuntimeRow row : rows) {
                if (row.executorInfo() != null) {
                    map.put(row.executorInfo(), row);
                }
            }
            return map;
        } catch (DataAccessException e) {
            log.debug("SnailJob 实时监控不可用: {}", e.getMessage());
            return null;
        }
    }

    private boolean isMonitorAvailable() {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM snail_job.sj_job LIMIT 1", Integer.class);
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private AdminSnailJobTaskVO toTaskVo(SnailJobJobDefinition def, JobRuntimeRow runtime) {
        AdminSnailJobTaskVO.AdminSnailJobTaskVOBuilder builder = AdminSnailJobTaskVO.builder()
                .executorName(def.executorName())
                .jobName(def.jobName())
                .description(def.description())
                .triggerType(def.triggerType().name())
                .triggerInterval(def.triggerInterval())
                .executorTimeoutSeconds(def.executorTimeoutSeconds())
                .registered(runtime != null);
        if (runtime != null) {
            builder.jobId(runtime.jobId())
                    .jobStatus(runtime.jobStatus())
                    .nextTriggerAt(fromEpochMillis(runtime.nextTriggerAt()))
                    .lastBatchId(runtime.lastBatchId())
                    .lastBatchStatus(batchStatusName(runtime.lastBatchStatus()))
                    .lastExecutionAt(fromEpochMillis(runtime.executionAt()))
                    .lastDurationMs(runtime.durationMs());
        }
        return builder.build();
    }

    private AdminSnailJobBatchVO mapBatch(ResultSet rs) throws SQLException {
        return AdminSnailJobBatchVO.builder()
                .id(rs.getLong("id"))
                .jobId(rs.getLong("job_id"))
                .jobName(rs.getString("job_name"))
                .batchStatus(batchStatusName(rs.getInt("task_batch_status")))
                .executionAt(fromEpochMillis(rs.getLong("execution_at")))
                .createDt(toLocalDateTime(rs.getTimestamp("create_dt")))
                .operationReason(rs.getInt("operation_reason"))
                .durationMs(rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null)
                .build();
    }

    private static LocalDateTime fromEpochMillis(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String batchStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return batchStatusName(status.intValue());
    }

    private static String batchStatusName(int status) {
        return switch (status) {
            case 1 -> "WAITING";
            case 2 -> "RUNNING";
            case 3 -> "SUCCESS";
            case 4 -> "FAIL";
            case 5 -> "STOP";
            case 6 -> "CANCEL";
            default -> "UNKNOWN";
        };
    }

    private record JobRuntimeRow(
            Long jobId,
            String executorInfo,
            Integer jobStatus,
            long nextTriggerAt,
            Long lastBatchId,
            Integer lastBatchStatus,
            long executionAt,
            Long durationMs
    ) {
    }
}
