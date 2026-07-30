package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminPermissionVO;
import com.linkx.server.service.admin.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
