package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminActorContext;
import com.linkx.server.common.admin.AdminExportModule;
import com.linkx.server.entity.admin.SysAdminExportJob;
import com.linkx.server.mapper.admin.SysAdminExportJobMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 异步执行导出任务（独立 Bean，避免 @Async 自调用失效）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminExportJobRunner {

    private final SysAdminExportJobMapper exportJobMapper;
    private final AdminExportCsvBuilder csvBuilder;
    private final AdminEventPublisher adminEventPublisher;

    @Async("adminExportExecutor")
    public void run(Long jobId) {
        SysAdminExportJob job = exportJobMapper.selectOneById(jobId);
        if (job == null || job.getDeleted() != null && job.getDeleted() == 1) {
            return;
        }
        if (!SysAdminExportJob.STATUS_PENDING.equals(job.getStatus())) {
            return;
        }

        Date now = new Date();
        int claimed = exportJobMapper.updateByQuery(
                SysAdminExportJob.builder().status(SysAdminExportJob.STATUS_RUNNING).updateTime(now).build(),
                QueryWrapper.create()
                        .where(SysAdminExportJob::getId).eq(jobId)
                        .and(SysAdminExportJob::getStatus).eq(SysAdminExportJob.STATUS_PENDING)
        );
        if (claimed <= 0) {
            return;
        }

        AdminActorContext.setUserId(job.getRequesterId());
        try {
            AdminExportModule module = AdminExportModule.fromCode(job.getModule());
            AdminExportCsvBuilder.CsvPayload payload = csvBuilder.build(module, job.getQueryJson());
            SysAdminExportJob success = SysAdminExportJob.builder()
                    .status(SysAdminExportJob.STATUS_SUCCESS)
                    .rowCount(payload.rowCount())
                    .fileName(payload.fileName())
                    .contentBytes(payload.bytes())
                    .errorMessage(null)
                    .updateTime(new Date())
                    .build();
            exportJobMapper.updateByQuery(success, QueryWrapper.create().where(SysAdminExportJob::getId).eq(jobId));
            adminEventPublisher.publish("export_ready", jobId,
                    "{\"module\":\"" + module.getCode() + "\",\"fileName\":\"" + escape(payload.fileName()) + "\"}");
        } catch (Exception e) {
            log.warn("异步导出失败: jobId={}, err={}", jobId, e.getMessage());
            String msg = e.getMessage() == null ? "export failed" : e.getMessage();
            if (msg.length() > 480) {
                msg = msg.substring(0, 480);
            }
            exportJobMapper.updateByQuery(
                    SysAdminExportJob.builder()
                            .status(SysAdminExportJob.STATUS_FAILED)
                            .errorMessage(msg)
                            .updateTime(new Date())
                            .build(),
                    QueryWrapper.create().where(SysAdminExportJob::getId).eq(jobId)
            );
            adminEventPublisher.publish("export_failed", jobId,
                    "{\"error\":\"" + escape(msg) + "\"}");
        } finally {
            AdminActorContext.clear();
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
