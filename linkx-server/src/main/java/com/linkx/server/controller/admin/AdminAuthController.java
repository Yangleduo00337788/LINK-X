package com.linkx.server.controller.admin;

import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminProfileUpdateDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.dto.AdminTotpChallengeDTO;
import com.linkx.server.controller.admin.dto.AdminTotpConfirmDTO;
import com.linkx.server.controller.admin.dto.AdminTotpDisableDTO;
import com.linkx.server.controller.admin.dto.AdminTotpLoginDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminTotpSetupVO;
import com.linkx.server.controller.admin.vo.AdminUserProfileVO;
import com.linkx.server.controller.vo.AuthConfigVO;
import com.linkx.server.service.admin.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(name = "管理端-认证")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final LinkxProperties linkxProperties;

    @Operation(summary = "管理端认证配置（匿名可读）")
    @GetMapping("/config")
    public Result<AuthConfigVO> config() {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        return Result.success(AuthConfigVO.builder()
                .captchaEnabled(auth.isAdminCaptchaEnabled())
                .totpRequired(auth.isAdminTotpRequired())
                .passwordPolicy(AuthConfigVO.PasswordPolicy.builder()
                        .minLength(auth.getPasswordMinLength())
                        .maxLength(auth.getPasswordMaxLength())
                        .requireUpperLower(auth.isPasswordRequireUpperLower())
                        .requireDigit(auth.isPasswordRequireDigit())
                        .requireSpecial(auth.isPasswordRequireSpecial())
                        .build())
                .build());
    }

    @Operation(summary = "管理员登录")
    @AuditAction(operationType = "LOGIN", description = "管理端登录")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        return Result.success(adminAuthService.login(dto, request, response));
    }

    @Operation(summary = "管理员 TOTP 二次验证登录")
    @AuditAction(operationType = "LOGIN", description = "管理端 TOTP 登录")
    @PostMapping("/login/totp")
    public Result<AdminLoginVO> loginTotp(@Valid @RequestBody AdminTotpLoginDTO dto,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        return Result.success(adminAuthService.verifyTotpLogin(dto, request, response));
    }

    @Operation(summary = "登录挑战下开始绑定 TOTP")
    @PostMapping("/totp/setup-challenge")
    public Result<AdminTotpSetupVO> setupTotpChallenge(@Valid @RequestBody AdminTotpChallengeDTO dto) {
        return Result.success(adminAuthService.beginTotpSetupWithChallenge(dto));
    }

    @Operation(summary = "登录挑战下确认绑定 TOTP 并签发会话")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "强制绑定管理端 TOTP")
    @PostMapping("/totp/confirm-challenge")
    public Result<AdminLoginVO> confirmTotpChallenge(@Valid @RequestBody AdminTotpConfirmDTO dto,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        return Result.success(adminAuthService.confirmTotp(null, dto, request, response));
    }

    @Operation(summary = "登录后开始绑定 TOTP")
    @PostMapping("/totp/setup")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminTotpSetupVO> setupTotp(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.beginTotpSetup(userId));
    }

    @Operation(summary = "确认启用 TOTP（已登录）")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "启用管理端 TOTP")
    @PostMapping("/totp/confirm")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminUserProfileVO> confirmTotp(@Valid @RequestBody AdminTotpConfirmDTO dto,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        Long userId = (Long) request.getAttribute("userId");
        dto.setChallengeToken(null);
        AdminLoginVO vo = adminAuthService.confirmTotp(userId, dto, request, response);
        return Result.success(vo.getUser());
    }

    @Operation(summary = "关闭 TOTP")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "关闭管理端 TOTP")
    @PostMapping("/totp/disable")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminUserProfileVO> disableTotp(@Valid @RequestBody AdminTotpDisableDTO dto,
                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.disableTotp(userId, dto));
    }

    @Operation(summary = "获取当前管理员信息")
    @GetMapping("/me")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminUserProfileVO> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.me(userId));
    }

    @Operation(summary = "更新当前管理员资料")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "管理端更新个人资料")
    @PutMapping("/profile")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminUserProfileVO> updateProfile(@Valid @RequestBody AdminProfileUpdateDTO dto,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.updateProfile(userId, dto));
    }

    @Operation(summary = "获取当前管理员菜单")
    @GetMapping("/menus")
    @RequireRole({"admin", "super_admin"})
    public Result<List<AdminMenuTreeVO>> menus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.menus(userId));
    }

    @Operation(summary = "获取当前管理员权限码")
    @GetMapping("/permissions")
    @RequireRole({"admin", "super_admin"})
    public Result<Set<String>> permissions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.permissions(userId));
    }

    @Operation(summary = "管理员退出登录")
    @AuditAction(operationType = "LOGOUT", description = "管理端退出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) AdminLogoutDTO dto,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        adminAuthService.logout(dto, authorization, request, response);
        return Result.success(null);
    }

    @Operation(summary = "刷新管理员令牌")
    @PostMapping("/refresh")
    public Result<AdminLoginVO> refresh(@RequestBody(required = false) @Valid AdminRefreshDTO dto,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        return Result.success(adminAuthService.refresh(dto, request, response));
    }
}
