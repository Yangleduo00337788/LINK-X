package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.RbacConstants;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CreateRoleDTO;
import com.linkx.server.entity.SysRole;
import com.linkx.server.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RBAC 角色权限管理接口。
 * <p>
 * 提供角色创建、角色列表、用户角色分配/移除、用户权限查询能力。
 * 类级别 {@link RequireRole} 限定全部接口仅系统管理员可访问，
 * 由 {@code RoleRequiredAspect} 拦截校验。
 * </p>
 */
@RestController
@Tag(name = "${openapi.tag.rbac}")
@RequestMapping("/rbac")
@RequiredArgsConstructor
@RequireRole(RbacConstants.ROLE_ADMIN)
public class RbacController {

    private final RbacService rbacService;

    /**
     * 创建角色。
     *
     * @param dto     角色信息
     * @param request HTTP 请求（取操作人 ID）
     * @return 新建角色
     */
    @PostMapping("/role")
    public Result<SysRole> createRole(@Valid @RequestBody CreateRoleDTO dto, HttpServletRequest request) {
        Long createBy = (Long) request.getAttribute("userId");
        SysRole role = rbacService.createRole(dto.getRoleCode(), dto.getRoleName(), dto.getDescription(), createBy);
        return Result.success(role);
    }

    /**
     * 查询全部启用角色。
     *
     * @return 角色列表
     */
    @GetMapping("/role")
    public Result<List<SysRole>> listRoles() {
        return Result.success(rbacService.listAllRoles());
    }

    /**
     * 为用户分配角色（幂等）。
     *
     * @param userId  用户 ID
     * @param roleId  角色 ID
     * @param request HTTP 请求（取操作人 ID）
     * @return 操作结果
     */
    @PostMapping("/user/{userId}/role/{roleId}")
    public Result<Void> grantRole(@PathVariable Long userId,
                                  @PathVariable Long roleId,
                                  HttpServletRequest request) {
        Long createBy = (Long) request.getAttribute("userId");
        rbacService.grantRoleById(userId, roleId, createBy);
        return Result.success(null);
    }

    /**
     * 移除用户角色（幂等）。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 操作结果
     */
    @DeleteMapping("/user/{userId}/role/{roleId}")
    public Result<Void> revokeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        rbacService.revokeRoleById(userId, roleId);
        return Result.success(null);
    }

    /**
     * 查询用户权限编码列表。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    @GetMapping("/user/{userId}/permissions")
    public Result<List<String>> userPermissions(@PathVariable Long userId) {
        return Result.success(rbacService.getUserPermissionCodes(userId));
    }
}
