package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-用户管理")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "查询用户列表")
    @GetMapping
    @RequirePermission("admin:user:list")
    public Result<PageResultVO<AdminUserListVO>> list(@Valid AdminUserQueryDTO query) {
        return Result.success(adminUserService.list(query));
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:user:view")
    public Result<AdminUserDetailVO> detail(@PathVariable Long id) {
        return Result.success(adminUserService.detail(id));
    }

    @Operation(summary = "更新用户资料")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "管理端更新用户资料")
    @PutMapping("/{id}")
    @RequirePermission("admin:user:edit")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody AdminUserUpdateDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.update(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "冻结用户")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "冻结用户")
    @PostMapping("/{id}/freeze")
    @RequirePermission("admin:user:freeze")
    public Result<Void> freeze(@PathVariable Long id,
                               @RequestBody(required = false) AdminUserActionDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.freeze(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "解冻用户")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "解冻用户")
    @PostMapping("/{id}/unfreeze")
    @RequirePermission("admin:user:unfreeze")
    public Result<Void> unfreeze(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.unfreeze(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "封禁用户")
    @AuditAction(operationType = "BLACKLIST_ADD", description = "封禁用户")
    @PostMapping("/{id}/ban")
    @RequirePermission("admin:user:ban")
    public Result<Void> ban(@PathVariable Long id,
                            @RequestBody(required = false) AdminUserActionDTO dto,
                            HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.ban(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "解封用户")
    @AuditAction(operationType = "BLACKLIST_REMOVE", description = "解封用户")
    @PostMapping("/{id}/unban")
    @RequirePermission("admin:user:unban")
    public Result<Void> unban(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.unban(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "查询用户设备")
    @GetMapping("/{id}/devices")
    @RequirePermission("admin:user:device:list")
    public Result<List<DeviceVO>> devices(@PathVariable Long id) {
        return Result.success(adminUserService.devices(id));
    }

    @Operation(summary = "查询用户登录记录")
    @GetMapping("/{id}/logins")
    @RequirePermission("admin:user:login:list")
    public Result<PageResultVO<AdminLoginLogVO>> logins(@PathVariable Long id, @Valid AdminPageQueryDTO query) {
        return Result.success(adminUserService.logins(id, query));
    }
}
