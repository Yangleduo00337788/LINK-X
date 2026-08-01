package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignMenuDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignPermissionDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignUserDTO;
import com.linkx.server.controller.admin.dto.AdminRoleDTO;
import com.linkx.server.controller.admin.vo.AdminRoleUserVO;
import com.linkx.server.controller.admin.vo.AdminRoleVO;
import com.linkx.server.service.admin.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

@Tag(name = "管理端-角色管理")
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @Operation(summary = "查询角色列表")
    @GetMapping
    @RequirePermission("admin:role:list")
    public Result<PageResultVO<AdminRoleVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminRoleService.list(query));
    }

    @Operation(summary = "查询角色详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:role:list")
    public Result<AdminRoleVO> detail(@PathVariable Long id) {
        return Result.success(adminRoleService.detail(id));
    }

    @Operation(summary = "新增角色")
    @AuditAction(operationType = "ROLE_GRANT", description = "新增角色")
    @PostMapping
    @RequirePermission("admin:role:create")
    public Result<Long> create(@Valid @RequestBody AdminRoleDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRoleService.create(dto, operatorId));
    }

    @Operation(summary = "编辑角色")
    @AuditAction(operationType = "ROLE_GRANT", description = "编辑角色")
    @PutMapping("/{id}")
    @RequirePermission("admin:role:edit")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody AdminRoleDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminRoleService.update(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "删除角色")
    @AuditAction(operationType = "ROLE_REVOKE", description = "删除角色")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:role:delete")
    @RequireStepUp("admin:role:delete")
    public Result<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return Result.success(null);
    }

    @Operation(summary = "查询角色菜单")
    @GetMapping("/{id}/menus")
    @RequirePermission("admin:role:list")
    public Result<List<Long>> menus(@PathVariable Long id) {
        return Result.success(adminRoleService.getRoleMenuIds(id));
    }

    @Operation(summary = "角色菜单授权")
    @AuditAction(operationType = "ROLE_GRANT", description = "角色菜单授权")
    @PutMapping("/{id}/menus")
    @RequirePermission("admin:role:assign-menu")
    @RequireStepUp("admin:role:assign-menu")
    public Result<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody AdminRoleAssignMenuDTO dto) {
        adminRoleService.assignMenus(id, dto);
        return Result.success(null);
    }

    @Operation(summary = "查询角色权限")
    @GetMapping("/{id}/permissions")
    @RequirePermission("admin:role:list")
    public Result<List<Long>> permissions(@PathVariable Long id) {
        return Result.success(adminRoleService.getRolePermissionIds(id));
    }

    @Operation(summary = "角色权限授权")
    @AuditAction(operationType = "ROLE_GRANT", description = "角色权限授权")
    @PutMapping("/{id}/permissions")
    @RequirePermission("admin:role:assign-permission")
    @RequireStepUp("admin:role:assign-permission")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AdminRoleAssignPermissionDTO dto) {
        adminRoleService.assignPermissions(id, dto);
        return Result.success(null);
    }

    @Operation(summary = "查询角色用户")
    @GetMapping("/{id}/users")
    @RequirePermission("admin:role:list")
    public Result<List<AdminRoleUserVO>> users(@PathVariable Long id) {
        return Result.success(adminRoleService.listRoleUsers(id));
    }

    @Operation(summary = "角色绑定用户")
    @AuditAction(operationType = "ROLE_GRANT", description = "角色绑定用户")
    @PutMapping("/{id}/users")
    @RequirePermission("admin:role:assign-user")
    @RequireStepUp("admin:role:assign-user")
    public Result<Void> assignUsers(@PathVariable Long id,
                                    @Valid @RequestBody AdminRoleAssignUserDTO dto,
                                    HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminRoleService.assignUsers(id, dto, operatorId);
        return Result.success(null);
    }
}
