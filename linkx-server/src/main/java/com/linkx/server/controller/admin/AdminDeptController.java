package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminDeptDTO;
import com.linkx.server.controller.admin.vo.AdminDeptVO;
import com.linkx.server.service.admin.AdminDeptService;
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

@Tag(name = "管理端-部门管理")
@RestController
@RequestMapping("/admin/depts")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminDeptController {

    private final AdminDeptService adminDeptService;

    @Operation(summary = "部门树")
    @GetMapping
    @RequirePermission("admin:dept:list")
    public Result<List<AdminDeptVO>> tree() {
        return Result.success(adminDeptService.tree());
    }

    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:dept:list")
    public Result<AdminDeptVO> detail(@PathVariable Long id) {
        return Result.success(adminDeptService.detail(id));
    }

    @Operation(summary = "新增部门")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增部门")
    @PostMapping
    @RequirePermission("admin:dept:create")
    public Result<Long> create(@Valid @RequestBody AdminDeptDTO dto, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminDeptService.create(dto, operatorId));
    }

    @Operation(summary = "编辑部门")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "编辑部门")
    @PutMapping("/{id}")
    @RequirePermission("admin:dept:edit")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody AdminDeptDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminDeptService.update(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "删除部门")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除部门")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:dept:delete")
    public Result<Void> delete(@PathVariable Long id) {
        adminDeptService.delete(id);
        return Result.success(null);
    }
}
