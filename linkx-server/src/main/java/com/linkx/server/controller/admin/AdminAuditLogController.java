package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;
import com.linkx.server.service.admin.AdminAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-操作日志")
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @Operation(summary = "查询操作日志")
    @GetMapping
    @RequirePermission("admin:audit:list")
    public Result<PageResultVO<AdminOperationLogVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminAuditLogService.listAuditLogs(query));
    }

    @Operation(summary = "查询操作日志详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:audit:list")
    public Result<AdminOperationLogVO> detail(@PathVariable Long id) {
        return Result.success(adminAuditLogService.auditDetail(id));
    }
}
