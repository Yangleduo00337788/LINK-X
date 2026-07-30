package com.linkx.server.controller.admin;

import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminUserProfileVO;
import com.linkx.server.service.admin.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "管理员登录")
    @AuditAction(operationType = "LOGIN", description = "管理端登录")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        return Result.success(adminAuthService.login(dto, request, response));
    }

    @Operation(summary = "获取当前管理员信息")
    @GetMapping("/me")
    @RequireRole({"admin", "super_admin"})
    public Result<AdminUserProfileVO> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(adminAuthService.me(userId));
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
