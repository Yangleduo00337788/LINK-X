package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminReviewBatchDTO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.controller.admin.vo.AdminReviewVO;
import com.linkx.server.service.admin.AdminReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-内容审核")
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @Operation(summary = "查询审核任务列表")
    @GetMapping
    @RequirePermission("admin:review:list")
    public Result<PageResultVO<AdminReviewVO>> list(@Valid AdminReviewQueryDTO query) {
        return Result.success(adminReviewService.list(query));
    }

    @Operation(summary = "导出审核任务 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:review:export")
    public ResponseEntity<byte[]> export(@Valid AdminReviewQueryDTO query) {
        List<AdminReviewVO> items = adminReviewService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminReviewVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getSourceType()),
                    AdminCsvResponses.cell(item.getTitle()),
                    AdminCsvResponses.cell(item.getReporterUsername()),
                    AdminCsvResponses.cell(item.getTargetId()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getRiskLevel()),
                    AdminCsvResponses.cell(item.getResolution()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("reviews",
                List.of("id", "sourceType", "title", "reporter", "targetId", "status", "riskLevel", "resolution", "createTime"),
                rows);
    }

    @Operation(summary = "查询审核任务详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:review:list")
    public Result<AdminReviewVO> detail(@PathVariable Long id) {
        return Result.success(adminReviewService.detail(id));
    }

    @Operation(summary = "审核通过")
    @AuditAction(operationType = "CONTENT_REVIEW", description = "审核通过")
    @PostMapping("/{id}/approve")
    @RequirePermission("admin:review:approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) @Valid AdminReviewResolveDTO dto,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminReviewService.approve(id, dto == null ? new AdminReviewResolveDTO() : dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "审核驳回")
    @AuditAction(operationType = "CONTENT_REVIEW", description = "审核驳回")
    @PostMapping("/{id}/reject")
    @RequirePermission("admin:review:reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) @Valid AdminReviewResolveDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminReviewService.reject(id, dto == null ? new AdminReviewResolveDTO() : dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "批量审核")
    @AuditAction(operationType = "CONTENT_REVIEW", description = "批量审核")
    @PostMapping("/batch")
    @RequirePermission("admin:review:batch")
    public Result<AdminReviewBatchResultVO> batch(@Valid @RequestBody AdminReviewBatchDTO dto,
                                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminReviewService.batch(dto, operatorId));
    }
}
