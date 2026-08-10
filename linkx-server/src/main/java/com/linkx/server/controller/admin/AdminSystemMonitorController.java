package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.vo.AdminSystemMonitorOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSystemTableStatsVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorApiStatsVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorCacheVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorServiceVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorSqlVO;
import com.linkx.server.controller.admin.vo.monitor.AdminMonitorTaskStatsVO;
import com.linkx.server.service.admin.AdminSystemMonitorMetricsService;
import com.linkx.server.service.admin.AdminSystemMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-系统监控")
@RestController
@RequestMapping("/admin/system-monitor")
@RequiredArgsConstructor
public class AdminSystemMonitorController {

    private final AdminSystemMonitorService systemMonitorService;
    private final AdminSystemMonitorMetricsService metricsService;

    @Operation(summary = "系统监控总览（轻量）")
    @GetMapping
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminSystemMonitorOverviewVO> overview() {
        return Result.success(systemMonitorService.overview());
    }

    @Operation(summary = "数据库表体量")
    @GetMapping("/tables")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminSystemTableStatsVO> tables(
            @Parameter(description = "是否绕过缓存强制刷新")
            @RequestParam(defaultValue = "false") boolean refresh) {
        return Result.success(systemMonitorService.tableStats(refresh));
    }

    @Operation(summary = "缓存监控")
    @GetMapping("/cache")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminMonitorCacheVO> cache(
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(metricsService.cache(hours));
    }

    @Operation(summary = "服务监控")
    @GetMapping("/service")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminMonitorServiceVO> service(
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(metricsService.service(hours));
    }

    @Operation(summary = "API 访问统计")
    @GetMapping("/api-stats")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminMonitorApiStatsVO> apiStats(
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(metricsService.apiStats(days));
    }

    @Operation(summary = "定时任务监控")
    @GetMapping("/tasks")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminMonitorTaskStatsVO> tasks(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(metricsService.taskStats(days));
    }

    @Operation(summary = "SQL 监控")
    @GetMapping("/sql")
    @RequirePermission("admin:system-monitor:view")
    public Result<AdminMonitorSqlVO> sql(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(metricsService.sql(hours, limit));
    }
}
