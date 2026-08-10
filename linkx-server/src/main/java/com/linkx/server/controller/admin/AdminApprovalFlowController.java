package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminApprovalFlowDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalFlowVO;
import com.linkx.server.service.admin.AdminApprovalFlowService;
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

@Tag(name = "管理端-审批流程")
@RestController
@RequestMapping("/admin/approval-flows")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminApprovalFlowController {

    private final AdminApprovalFlowService adminApprovalFlowService;

    @Operation(summary = "审批流程列表")
    @GetMapping
    @RequirePermission("admin:approval-flow:list")
    public Result<PageResultVO<AdminApprovalFlowVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminApprovalFlowService.list(query));
    }

    @Operation(summary = "审批流程详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:approval-flow:list")
    public Result<AdminApprovalFlowVO> detail(@PathVariable Long id) {
        return Result.success(adminApprovalFlowService.detail(id));
    }

    @Operation(summary = "新增审批流程")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增审批流程")
    @PostMapping
    @RequirePermission("admin:approval-flow:create")
    public Result<AdminApprovalFlowVO> create(@Valid @RequestBody AdminApprovalFlowDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalFlowService.create(dto, operatorId));
    }

    @Operation(summary = "更新审批流程")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "更新审批流程")
    @PutMapping("/{id}")
    @RequirePermission("admin:approval-flow:edit")
    public Result<AdminApprovalFlowVO> update(@PathVariable Long id,
                                              @Valid @RequestBody AdminApprovalFlowDTO dto,
                                              HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalFlowService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除审批流程")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除审批流程")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:approval-flow:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminApprovalFlowService.delete(id, operatorId);
        return Result.success(null);
    }
}
