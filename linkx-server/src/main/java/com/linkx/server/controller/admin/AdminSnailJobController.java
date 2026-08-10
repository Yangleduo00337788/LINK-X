package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobBatchVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobLogVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobOverviewVO;
import com.linkx.server.service.admin.AdminSnailJobMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务监控：内置目录 + SnailJob 实时状态/批次/日志。
 * 修改 cron、启停、立即执行等写操作仍在 SnailJob 控制台。
 */
@RestController
@RequestMapping("/admin/scheduled-tasks")
@RequiredArgsConstructor
public class AdminSnailJobController {

    private final AdminSnailJobMonitorService monitorService;

    @GetMapping
    @RequirePermission("admin:scheduled-task:list")
    public Result<AdminSnailJobOverviewVO> overview() {
        return Result.success(monitorService.overview());
    }

    @GetMapping("/{jobId}/batches")
    @RequirePermission("admin:scheduled-task:list")
    public Result<PageResultVO<AdminSnailJobBatchVO>> batches(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(monitorService.listBatches(jobId, page, size));
    }

    @GetMapping("/batches/{batchId}/logs")
    @RequirePermission("admin:scheduled-task:list")
    public Result<PageResultVO<AdminSnailJobLogVO>> logs(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.success(monitorService.listLogs(batchId, page, size));
    }
}
