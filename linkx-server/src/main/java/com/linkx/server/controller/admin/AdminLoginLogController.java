package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.service.admin.AdminAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-登录日志")
@RestController
@RequestMapping("/admin/login-logs")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminLoginLogController {

    private final AdminAuditLogService adminAuditLogService;

    @Operation(summary = "查询登录日志")
    @GetMapping
    @RequirePermission("admin:login-log:list")
    public Result<PageResultVO<AdminLoginLogVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminAuditLogService.listLoginLogs(query));
    }

    @Operation(summary = "导出登录日志 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:login-log:export")
    public ResponseEntity<byte[]> export(@Valid AdminPageQueryDTO query) {
        List<AdminLoginLogVO> items = adminAuditLogService.listLoginLogsForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminLoginLogVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getUserAgent()),
                    AdminCsvResponses.cell(item.getSuccess()),
                    AdminCsvResponses.cell(item.getReason()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("login-logs",
                List.of("id", "userId", "username", "ip", "userAgent", "success", "reason", "createTime"),
                rows);
    }

    @Operation(summary = "查询登录日志详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:login-log:list")
    public Result<AdminLoginLogVO> detail(@PathVariable Long id) {
        return Result.success(adminAuditLogService.loginDetail(id));
    }
}
