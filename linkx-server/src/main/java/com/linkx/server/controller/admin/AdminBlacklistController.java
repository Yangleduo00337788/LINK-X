package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-黑名单")
@RestController
@RequestMapping("/admin/blacklist")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin", "ops_admin", "audit_admin"})
public class AdminBlacklistController {

    private final AdminBlacklistService adminBlacklistService;

    @Operation(summary = "查询黑名单列表")
    @GetMapping
    @RequirePermission("admin:blacklist:list")
    public Result<PageResultVO<AdminBlacklistVO>> list(@Valid AdminBlacklistQueryDTO query) {
        return Result.success(adminBlacklistService.list(query));
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
    public Result<Void> add(@Valid @RequestBody AdminBlacklistAddDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminBlacklistService.add(dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "移出黑名单")
    @AuditAction(operationType = "BLACKLIST_REMOVE", description = "移出黑名单")
    @PostMapping("/{id}/release")
    @RequirePermission("admin:blacklist:remove")
    public Result<Void> release(@PathVariable Long id,
                                @RequestBody(required = false) AdminBlacklistReleaseDTO dto,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminBlacklistService.release(id, dto, operatorId);
        return Result.success(null);
    }
}
