package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchRuleDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchSimulateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchRuleVO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchSimulateVO;
import com.linkx.server.service.admin.AdminFeedbackDispatchRuleService;
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

@Tag(name = "管理端-反馈分流规则")
@RestController
@RequestMapping("/admin/feedback-dispatch-rules")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminFeedbackDispatchRuleController {

    private final AdminFeedbackDispatchRuleService adminFeedbackDispatchRuleService;

    @Operation(summary = "查询分流规则列表")
    @GetMapping
    @RequirePermission("admin:feedback-dispatch-rule:list")
    public Result<PageResultVO<AdminFeedbackDispatchRuleVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminFeedbackDispatchRuleService.list(query));
    }

    @Operation(summary = "查询分流规则详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:feedback-dispatch-rule:list")
    public Result<AdminFeedbackDispatchRuleVO> detail(@PathVariable Long id) {
        return Result.success(adminFeedbackDispatchRuleService.detail(id));
    }

    @Operation(summary = "新增分流规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增反馈分流规则")
    @PostMapping
    @RequirePermission("admin:feedback-dispatch-rule:create")
    public Result<AdminFeedbackDispatchRuleVO> create(@Valid @RequestBody AdminFeedbackDispatchRuleDTO dto,
                                                      HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminFeedbackDispatchRuleService.create(dto, operatorId));
    }

    @Operation(summary = "更新分流规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "更新反馈分流规则")
    @PutMapping("/{id}")
    @RequirePermission("admin:feedback-dispatch-rule:edit")
    public Result<AdminFeedbackDispatchRuleVO> update(@PathVariable Long id,
                                                      @Valid @RequestBody AdminFeedbackDispatchRuleDTO dto,
                                                      HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminFeedbackDispatchRuleService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除分流规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除反馈分流规则")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:feedback-dispatch-rule:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminFeedbackDispatchRuleService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "模拟分流规则")
    @PostMapping("/simulate")
    @RequirePermission("admin:feedback-dispatch-rule:simulate")
    public Result<AdminFeedbackDispatchSimulateVO> simulate(@Valid @RequestBody AdminFeedbackDispatchSimulateDTO dto) {
        return Result.success(adminFeedbackDispatchRuleService.simulate(dto));
    }
}
