package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminBlacklistAddDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistQueryDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistReleaseDTO;
import com.linkx.server.controller.admin.vo.AdminBlacklistVO;
import com.linkx.server.service.admin.AdminBlacklistService;
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

@Tag(name = "管理端-黑名单")
@RestController
@RequestMapping("/admin/blacklist")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminBlacklistController {

    private final AdminBlacklistService adminBlacklistService;

    @Operation(summary = "查询黑名单列表")
    @GetMapping
    @RequirePermission("admin:blacklist:list")
    public Result<PageResultVO<AdminBlacklistVO>> list(@Valid AdminBlacklistQueryDTO query) {
        return Result.success(adminBlacklistService.list(query));
    }

    @Operation(summary = "导出黑名单 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:blacklist:export")
    public ResponseEntity<byte[]> export(@Valid AdminBlacklistQueryDTO query) {
        List<AdminBlacklistVO> items = adminBlacklistService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminBlacklistVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getReason()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getCreatedByName()),
                    AdminCsvResponses.cell(item.getCreateTime()),
                    AdminCsvResponses.cell(item.getReleasedByName()),
                    AdminCsvResponses.cell(item.getReleasedAt()),
                    AdminCsvResponses.cell(item.getReleaseReason()),
            });
        }
        return AdminCsvResponses.csv("blacklist",
                List.of("id", "userId", "username", "nickname", "reason", "status",
                        "createdBy", "createTime", "releasedBy", "releasedAt", "releaseReason"),
                rows);
    }

    @Operation(summary = "查询黑名单详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:blacklist:list")
    public Result<AdminBlacklistVO> detail(@PathVariable Long id) {
        return Result.success(adminBlacklistService.detail(id));
    }

    @Operation(summary = "加入黑名单")
    @AuditAction(operationType = "BLACKLIST_ADD", description = "加入黑名单")
    @PostMapping
    @RequirePermission("admin:blacklist:add")
    @RequireStepUp("admin:blacklist:add")
    public Result<Void> add(@Valid @RequestBody AdminBlacklistAddDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminBlacklistService.add(dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "移出黑名单")
    @AuditAction(operationType = "BLACKLIST_REMOVE", description = "移出黑名单")
    @PostMapping("/{id}/release")
    @RequirePermission("admin:blacklist:remove")
    @RequireStepUp("admin:blacklist:remove")
    public Result<Void> release(@PathVariable Long id,
                                @RequestBody(required = false) AdminBlacklistReleaseDTO dto,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminBlacklistService.release(id, dto, operatorId);
        return Result.success(null);
    }
}
