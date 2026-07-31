package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-统计分析")
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin", "ops_admin", "audit_admin"})
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

    @Operation(summary = "导出统计总览 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:statistics:export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "天数，默认 14，范围 7-90")
            @RequestParam(defaultValue = "14") int days) {
        AdminStatisticOverviewVO ov = adminStatisticsService.overview(days);
        AdminStatisticUserVO users = adminStatisticsService.users(days);
        AdminStatisticContentVO content = adminStatisticsService.content(days);
        AdminStatisticRiskVO risk = adminStatisticsService.risk(days);
        AdminStatisticFeedbackVO feedback = adminStatisticsService.feedback(days);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"metric", "value"});
        rows.add(kv("days", days));
        rows.add(kv("totalUsers", ov.getTotalUsers()));
        rows.add(kv("activeUsers", ov.getActiveUsers()));
        rows.add(kv("onlineDevices", ov.getOnlineDevices()));
        rows.add(kv("pendingFeedback", ov.getPendingFeedback()));
        rows.add(kv("pendingReviews", ov.getPendingReviews()));
        rows.add(kv("riskEvents", ov.getRiskEvents()));
        rows.add(kv("todayNewUsers", ov.getTodayNewUsers()));
        rows.add(kv("todayMessages", ov.getTodayMessages()));
        rows.add(kv("todayLogins", ov.getTodayLogins()));
        rows.add(kv("totalMessages", ov.getTotalMessages()));
        rows.add(kv("totalUploads", ov.getTotalUploads()));
        rows.add(kv("closedFeedback", ov.getClosedFeedback()));
        rows.add(kv("newUsersInRange", users.getNewUsersInRange()));
        rows.add(kv("loginSuccessInRange", users.getLoginSuccessInRange()));
        rows.add(kv("loginFailInRange", users.getLoginFailInRange()));
        rows.add(kv("messagesInRange", content.getMessagesInRange()));
        rows.add(kv("momentsInRange", content.getMomentsInRange()));
        rows.add(kv("uploadsInRange", content.getUploadsInRange()));
        rows.add(kv("sensitiveHitsInRange", risk.getSensitiveHitsInRange()));
        rows.add(kv("messageStormsInRange", risk.getMessageStormsInRange()));
        rows.add(kv("loginLocksInRange", risk.getLoginLocksInRange()));
        rows.add(kv("rateLimitsInRange", risk.getRateLimitsInRange()));
        rows.add(kv("createdFeedbackInRange", feedback.getCreatedInRange()));
        rows.add(kv("repliedFeedbackInRange", feedback.getRepliedInRange()));
        rows.add(kv("closedFeedbackInRange", feedback.getClosedInRange()));

        return AdminCsvResponses.csv("statistics", List.of("metric", "value"), rows);
    }

    private static String[] kv(String key, Object value) {
        return new String[]{key, AdminCsvResponses.cell(value)};
    }
}
