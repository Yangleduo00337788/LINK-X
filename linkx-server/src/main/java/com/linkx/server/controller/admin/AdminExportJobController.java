package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminExportJobCreateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminExportJobVO;
import com.linkx.server.entity.admin.SysAdminExportJob;
import com.linkx.server.service.admin.AdminExportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Tag(name = "管理端-异步导出")
@RestController
@RequestMapping("/admin/export-jobs")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminExportJobController {

    private final AdminExportJobService adminExportJobService;

    @Operation(summary = "创建异步导出任务")
    @PostMapping
    @AuditAction(operationType = "DATA_EXPORT", description = "创建异步导出任务", logParams = true)
    public Result<AdminExportJobVO> create(@Valid @RequestBody AdminExportJobCreateDTO dto,
                                           HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminExportJobService.create(dto, operatorId));
    }

    @Operation(summary = "我的导出任务列表")
    @GetMapping
    public Result<PageResultVO<AdminExportJobVO>> list(@Valid AdminPageQueryDTO query,
                                                       HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminExportJobService.list(query, operatorId));
    }

    @Operation(summary = "导出任务详情")
    @GetMapping("/{id}")
    public Result<AdminExportJobVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminExportJobService.detail(id, operatorId));
    }

    @Operation(summary = "下载已完成的导出文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        SysAdminExportJob job = adminExportJobService.loadDownloadable(id, operatorId);
        String filename = job.getFileName() == null ? "export.csv" : job.getFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(job.getContentBytes());
    }
}
