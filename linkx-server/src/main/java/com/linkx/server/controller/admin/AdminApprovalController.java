package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminApprovalActionDTO;
import com.linkx.server.controller.admin.dto.AdminApprovalStartDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalInboxItemVO;
import com.linkx.server.controller.admin.vo.AdminApprovalInstanceVO;
import com.linkx.server.service.admin.AdminApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-审批待办")
@RestController
@RequestMapping("/admin/approvals")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminApprovalController {

    private final AdminApprovalService adminApprovalService;

    @Operation(summary = "我的审批待办")
    @GetMapping("/inbox")
    @RequirePermission("admin:approval:inbox")
    public Result<PageResultVO<AdminApprovalInboxItemVO>> inbox(@Valid AdminPageQueryDTO query,
                                                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalService.inbox(query, operatorId));
    }

    @Operation(summary = "抄送我的")
    @GetMapping("/cc")
    @RequirePermission("admin:approval:inbox")
    public Result<PageResultVO<AdminApprovalInboxItemVO>> ccInbox(@Valid AdminPageQueryDTO query,
                                                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalService.ccInbox(query, operatorId));
    }

    @Operation(summary = "发起审批")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "发起审批")
    @PostMapping("/start")
    @RequirePermission("admin:approval:start")
    public Result<AdminApprovalInstanceVO> start(@Valid @RequestBody AdminApprovalStartDTO dto,
                                                 HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalService.start(dto, operatorId));
    }

    @Operation(summary = "审批实例详情")
    @GetMapping("/instances/{id}")
    @RequirePermission("admin:approval:inbox")
    public Result<AdminApprovalInstanceVO> instanceDetail(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminApprovalService.instanceDetail(id, operatorId));
    }

    @Operation(summary = "审批通过")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "审批通过")
    @PostMapping("/records/{id}/approve")
    @RequirePermission("admin:approval:action")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) AdminApprovalActionDTO dto,
                                HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminApprovalService.approve(id, dto, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "审批驳回")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "审批驳回")
    @PostMapping("/records/{id}/reject")
    @RequirePermission("admin:approval:action")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) AdminApprovalActionDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminApprovalService.reject(id, dto, operatorId);
        return Result.success(null);
    }
}
