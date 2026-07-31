package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminSensitiveWordDTO;
import com.linkx.server.controller.admin.vo.AdminSensitiveWordVO;
import com.linkx.server.service.admin.AdminSensitiveWordService;
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

@Tag(name = "管理端-敏感词")
@RestController
@RequestMapping("/admin/sensitive-words")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminSensitiveWordController {

    private final AdminSensitiveWordService adminSensitiveWordService;

    @Operation(summary = "查询敏感词列表")
    @GetMapping
    @RequirePermission("admin:sensitive-word:list")
    public Result<PageResultVO<AdminSensitiveWordVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminSensitiveWordService.list(query));
    }

    @Operation(summary = "查询敏感词详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:sensitive-word:list")
    public Result<AdminSensitiveWordVO> detail(@PathVariable Long id) {
        return Result.success(adminSensitiveWordService.detail(id));
    }

    @Operation(summary = "新增敏感词")
    @AuditAction(operationType = "SENSITIVE_WORD_UPDATE", description = "新增敏感词")
    @PostMapping
    @RequirePermission("admin:sensitive-word:create")
    public Result<AdminSensitiveWordVO> create(@Valid @RequestBody AdminSensitiveWordDTO dto,
                                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSensitiveWordService.create(dto, operatorId));
    }

    @Operation(summary = "更新敏感词")
    @AuditAction(operationType = "SENSITIVE_WORD_UPDATE", description = "更新敏感词")
    @PutMapping("/{id}")
    @RequirePermission("admin:sensitive-word:edit")
    public Result<AdminSensitiveWordVO> update(@PathVariable Long id,
                                               @Valid @RequestBody AdminSensitiveWordDTO dto,
                                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminSensitiveWordService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除敏感词")
    @AuditAction(operationType = "SENSITIVE_WORD_UPDATE", description = "删除敏感词")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:sensitive-word:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminSensitiveWordService.delete(id, operatorId);
        return Result.success(null);
    }
}
