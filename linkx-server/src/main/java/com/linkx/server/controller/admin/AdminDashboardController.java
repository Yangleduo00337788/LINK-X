package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-仪表盘")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "仪表盘摘要")
    @GetMapping("/summary")
    @RequirePermission("admin:dashboard:view")
    public Result<AdminDashboardSummaryVO> summary() {
        return Result.success(adminDashboardService.summary());
    }
}
