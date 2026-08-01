package com.linkx.server.controller.admin;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminDeviceBindingDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserResetPasswordDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.admin.vo.AdminUserResetPasswordVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "管理端-用户管理")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final LinkxProperties linkxProperties;

    @Operation(summary = "查询用户列表")
    @GetMapping
    @RequirePermission("admin:user:list")
    public Result<PageResultVO<AdminUserListVO>> list(@Valid AdminUserQueryDTO query) {
        return Result.success(adminUserService.list(query));
    }

    @Operation(summary = "导出用户 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:user:export")
    public ResponseEntity<byte[]> export(@Valid AdminUserQueryDTO query) {
        List<AdminUserListVO> items = adminUserService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminUserListVO item : items) {
            String roles = item.getRoles() == null ? "" : item.getRoles().stream().collect(Collectors.joining("|"));
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getEmail()),
                    AdminCsvResponses.cell(item.getPhone()),
                    AdminCsvResponses.cell(item.getStatus()),
                    roles,
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("users",
                List.of("id", "username", "nickname", "email", "phone", "status", "roles", "createTime"),
                rows);
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
    @RequireStepUp("admin:user:freeze")
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
    @RequireStepUp("admin:user:unfreeze")
    public Result<Void> unfreeze(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.unfreeze(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "封禁用户")
    @AuditAction(operationType = "BLACKLIST_ADD", description = "封禁用户")
    @PostMapping("/{id}/ban")
    @RequirePermission("admin:user:ban")
    @RequireStepUp("admin:user:ban")
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
    @RequireStepUp("admin:user:unban")
    public Result<Void> unban(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.unban(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "重置用户密码", description = "不可重置管理员账号；会吊销该用户全部会话。newPassword 为空时生成临时密码并仅返回一次")
    @AuditAction(operationType = "RESET_PASSWORD", description = "管理端重置用户密码")
    @PostMapping("/{id}/reset-password")
    @RequirePermission("admin:user:reset-password")
    @RequireStepUp("admin:user:reset-password")
    public Result<AdminUserResetPasswordVO> resetPassword(@PathVariable Long id,
                                                          @RequestBody(required = false) @Valid AdminUserResetPasswordDTO dto,
                                                          HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminUserService.resetPassword(id, dto, operatorId));
    }

    @Operation(summary = "查询用户设备")
    @GetMapping("/{id}/devices")
    @RequirePermission("admin:user:device:list")
    public Result<List<DeviceVO>> devices(@PathVariable Long id) {
        return Result.success(adminUserService.devices(id));
    }

    @Operation(summary = "设置用户设备强绑定")
    @AuditAction(operationType = "DEVICE_BINDING_TOGGLE", description = "设备强绑定开关")
    @PostMapping("/{id}/device-binding")
    @RequirePermission("admin:user:device-binding")
    @RequireStepUp("admin:user:device-binding")
    public Result<Void> setDeviceBinding(@PathVariable Long id,
                                         @Valid @RequestBody AdminDeviceBindingDTO dto,
                                         HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.setDeviceBindingEnabled(
                id,
                Boolean.TRUE.equals(dto.getEnabled()),
                operatorId,
                ClientIpResolver.resolve(request, linkxProperties),
                request.getHeader("User-Agent")
        );
        return Result.success(null);
    }

    @Operation(summary = "批准用户登录设备")
    @AuditAction(operationType = "DEVICE_APPROVE", description = "批准登录设备")
    @PostMapping("/{id}/devices/{deviceId}/approve")
    @RequirePermission("admin:user:device-approve")
    @RequireStepUp("admin:user:device-approve")
    public Result<Void> approveDevice(@PathVariable Long id,
                                      @PathVariable String deviceId,
                                      HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.approveDevice(
                id,
                deviceId,
                null,
                operatorId,
                ClientIpResolver.resolve(request, linkxProperties),
                request.getHeader("User-Agent")
        );
        return Result.success(null);
    }

    @Operation(summary = "撤销用户登录设备批准")
    @AuditAction(operationType = "DEVICE_REVOKE", description = "撤销登录设备")
    @PostMapping("/{id}/devices/{deviceId}/revoke")
    @RequirePermission("admin:user:device-approve")
    @RequireStepUp("admin:user:device-approve")
    public Result<Void> revokeDevice(@PathVariable Long id,
                                     @PathVariable String deviceId,
                                     HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminUserService.revokeDeviceApproval(
                id,
                deviceId,
                operatorId,
                ClientIpResolver.resolve(request, linkxProperties),
                request.getHeader("User-Agent")
        );
        return Result.success(null);
    }

    @Operation(summary = "查询用户登录记录")
    @GetMapping("/{id}/logins")
    @RequirePermission("admin:user:login:list")
    public Result<PageResultVO<AdminLoginLogVO>> logins(@PathVariable Long id, @Valid AdminPageQueryDTO query) {
        return Result.success(adminUserService.logins(id, query));
    }
}
