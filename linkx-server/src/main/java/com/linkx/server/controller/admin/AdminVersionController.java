package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;
import com.linkx.server.service.admin.AdminVersionService;
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

@Tag(name = "管理端-版本管理")
@RestController
@RequestMapping("/admin/versions")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminVersionController {

    private final AdminVersionService adminVersionService;

    @Operation(summary = "查询版本列表")
    @GetMapping
    @RequirePermission("admin:version:list")
    public Result<PageResultVO<AdminVersionVO>> list(@Valid AdminVersionQueryDTO query) {
        return Result.success(adminVersionService.list(query));
    }

    @Operation(summary = "查询版本详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:version:view")
    public Result<AdminVersionVO> detail(@PathVariable Long id) {
        return Result.success(adminVersionService.detail(id));
    }

    @Operation(summary = "新增版本")
    @AuditAction(operationType = "VERSION_UPDATE", description = "新增版本")
    @PostMapping
    @RequirePermission("admin:version:create")
    public Result<AdminVersionVO> create(@Valid @RequestBody AdminVersionDTO dto,
                                         HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminVersionService.create(dto, operatorId));
    }

    @Operation(summary = "编辑版本")
    @AuditAction(operationType = "VERSION_UPDATE", description = "编辑版本")
    @PutMapping("/{id}")
    @RequirePermission("admin:version:edit")
    public Result<AdminVersionVO> update(@PathVariable Long id,
                                         @Valid @RequestBody AdminVersionDTO dto,
                                         HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminVersionService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除版本")
    @AuditAction(operationType = "VERSION_UPDATE", description = "删除版本")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:version:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminVersionService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "发布版本")
    @AuditAction(operationType = "VERSION_PUBLISH", description = "发布版本")
    @PostMapping("/{id}/publish")
    @RequirePermission("admin:version:publish")
    public Result<AdminVersionVO> publish(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminVersionService.publish(id, operatorId));
    }
}
