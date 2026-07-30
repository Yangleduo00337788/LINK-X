package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.ClientSideSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.RegisterSettingUpdateDTO;
import com.linkx.server.controller.admin.dto.TestForgotPasswordEmailDTO;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.service.admin.AdminSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-系统配置")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminSettingController {

    private final AdminSettingService adminSettingService;

    @Operation(summary = "查询系统配置")
    @GetMapping
    @RequirePermission("admin:setting:view")
    public Result<AdminSettingVO> get() {
        return Result.success(adminSettingService.getSettings());
    }

    @Operation(summary = "更新管理端配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新管理端配置")
    @PutMapping("/admin")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateAdmin(@Valid @RequestBody AdminSideSettingUpdateDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateAdminSide(dto, operatorId));
    }

    @Operation(summary = "更新客户端配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新客户端配置")
    @PutMapping("/client")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateClient(@Valid @RequestBody ClientSideSettingUpdateDTO dto,
                                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateClientSide(dto, operatorId));
    }

    @Operation(summary = "更新注册配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新注册配置")
    @PutMapping("/register")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateRegister(@Valid @RequestBody RegisterSettingUpdateDTO dto,
                                                 HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateRegister(dto, operatorId));
    }

    @Operation(summary = "更新登录配置")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新登录配置")
    @PutMapping("/login")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updateLogin(@Valid @RequestBody com.linkx.server.controller.admin.dto.LoginSettingUpdateDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updateLogin(dto, operatorId));
    }

    @Operation(summary = "更新密码策略")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "更新密码策略")
    @PutMapping("/password")
    @RequirePermission("admin:setting:edit")
    public Result<AdminSettingVO> updatePassword(
            @Valid @RequestBody com.linkx.server.controller.admin.dto.PasswordSettingUpdateDTO dto,
            HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSettingService.updatePassword(dto, operatorId));
    }

    @Operation(summary = "测试忘记密码邮件")
    @PostMapping("/test-forgot-password-email")
    @RequirePermission("admin:setting:edit")
    public Result<String> testForgotPasswordEmail(@Valid @RequestBody TestForgotPasswordEmailDTO dto) {
        return Result.success(adminSettingService.testForgotPasswordEmail(dto.getEmail()));
    }
}
