package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPermissionDTO;
import com.linkx.server.controller.admin.vo.AdminPermissionVO;
import com.linkx.server.service.admin.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-权限管理")
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminPermissionController {

    private final AdminRoleService adminRoleService;

    @Operation(summary = "查询权限列表")
    @GetMapping
    @RequirePermission("admin:permission:list")
    public Result<PageResultVO<AdminPermissionVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminRoleService.listPermissions(query));
    }

    @Operation(summary = "查询权限详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:permission:list")
    public Result<AdminPermissionVO> detail(@PathVariable Long id) {
        return Result.success(adminRoleService.permissionDetail(id));
    }

    @Operation(summary = "新增权限")
    @AuditAction(operationType = "ROLE_GRANT", description = "新增权限点")
    @PostMapping
    @RequirePermission("admin:permission:create")
    public Result<Long> create(@Valid @RequestBody AdminPermissionDTO dto) {
        return Result.success(adminRoleService.createPermission(dto));
    }

    @Operation(summary = "编辑权限")
    @AuditAction(operationType = "ROLE_GRANT", description = "编辑权限点")
    @PutMapping("/{id}")
    @RequirePermission("admin:permission:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdminPermissionDTO dto) {
        adminRoleService.updatePermission(id, dto);
        return Result.success(null);
    }

    @Operation(summary = "删除权限")
    @AuditAction(operationType = "ROLE_REVOKE", description = "删除权限点")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:permission:delete")
    public Result<Void> delete(@PathVariable Long id) {
        adminRoleService.deletePermission(id);
        return Result.success(null);
    }
}
