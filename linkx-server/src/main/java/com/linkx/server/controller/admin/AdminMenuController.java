package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminMenuDTO;
import com.linkx.server.controller.admin.dto.AdminMenuReorderDTO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminMenuVO;
import com.linkx.server.service.admin.AdminMenuService;
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

@Tag(name = "管理端-菜单管理")
@RestController
@RequestMapping("/admin/menus")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    @Operation(summary = "查询菜单树")
    @GetMapping
    @RequirePermission("admin:menu:list")
    public Result<List<AdminMenuTreeVO>> list() {
        return Result.success(adminMenuService.treeAll());
    }

    @Operation(summary = "查询菜单详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:menu:list")
    public Result<AdminMenuVO> detail(@PathVariable Long id) {
        return Result.success(adminMenuService.detail(id));
    }

    @Operation(summary = "新增菜单")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增管理端菜单")
    @PostMapping
    @RequirePermission("admin:menu:create")
    public Result<Long> create(@Valid @RequestBody AdminMenuDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminMenuService.create(dto, operatorId));
    }

    @Operation(summary = "编辑菜单")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "编辑管理端菜单")
    @PutMapping("/{id}")
    @RequirePermission("admin:menu:edit")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody AdminMenuDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminMenuService.update(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "删除菜单")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除管理端菜单")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:menu:delete")
    public Result<Void> delete(@PathVariable Long id) {
        adminMenuService.delete(id);
        return Result.success(null);
    }

    @Operation(summary = "调整菜单排序")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "调整管理端菜单排序")
    @PostMapping("/reorder")
    @RequirePermission("admin:menu:reorder")
    public Result<Void> reorder(@Valid @RequestBody AdminMenuReorderDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminMenuService.reorder(dto, operatorId);
        return Result.success(null);
    }
}
