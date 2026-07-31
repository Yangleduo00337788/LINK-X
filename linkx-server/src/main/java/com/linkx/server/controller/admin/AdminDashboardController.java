package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.vo.AdminDashboardRealtimeVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminPendingTaskVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;
import com.linkx.server.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-仪表盘")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin", "ops_admin", "audit_admin"})
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "仪表盘摘要")
    @GetMapping("/summary")
    @RequirePermission("admin:dashboard:view")
    public Result<AdminDashboardSummaryVO> summary() {
        return Result.success(adminDashboardService.summary());
    }

    @Operation(summary = "仪表盘趋势")
    @GetMapping("/trends")
    @RequirePermission("admin:dashboard:view")
    public Result<AdminTrendVO> trends(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminDashboardService.trends(days));
    }

    @Operation(summary = "实时指标")
    @GetMapping("/realtime")
    @RequirePermission("admin:dashboard:view")
    public Result<AdminDashboardRealtimeVO> realtime() {
        return Result.success(adminDashboardService.realtime());
    }

    @Operation(summary = "待处理任务")
    @GetMapping("/pending-tasks")
    @RequirePermission("admin:dashboard:view")
    public Result<List<AdminPendingTaskVO>> pendingTasks() {
        return Result.success(adminDashboardService.pendingTasks());
    }
}
