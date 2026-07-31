package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.service.admin.AdminStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-统计分析")
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    @Operation(summary = "统计总览")
    @GetMapping("/overview")
    @RequirePermission("admin:statistics:view")
    public Result<AdminStatisticOverviewVO> overview(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminStatisticsService.overview(days));
    }

    @Operation(summary = "用户统计")
    @GetMapping("/users")
    @RequirePermission("admin:statistics:view")
    public Result<AdminStatisticUserVO> users(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminStatisticsService.users(days));
    }

    @Operation(summary = "内容统计")
    @GetMapping("/content")
    @RequirePermission("admin:statistics:view")
    public Result<AdminStatisticContentVO> content(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminStatisticsService.content(days));
    }

    @Operation(summary = "风控统计")
    @GetMapping("/risk")
    @RequirePermission("admin:statistics:view")
    public Result<AdminStatisticRiskVO> risk(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminStatisticsService.risk(days));
    }

    @Operation(summary = "反馈统计")
    @GetMapping("/feedback")
    @RequirePermission("admin:statistics:view")
    public Result<AdminStatisticFeedbackVO> feedback(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(adminStatisticsService.feedback(days));
    }
}
