package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleSimulateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleSimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleVO;
import com.linkx.server.service.admin.AdminRiskRuleService;
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

@Tag(name = "管理端-风控自定义规则")
@RestController
@RequestMapping("/admin/risk-rules")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRiskRuleController {

    private final AdminRiskRuleService adminRiskRuleService;

    @Operation(summary = "查询风控规则列表")
    @GetMapping
    @RequirePermission("admin:risk-rule:list")
    public Result<PageResultVO<AdminRiskRuleVO>> list(@Valid AdminPageQueryDTO query) {
        return Result.success(adminRiskRuleService.list(query));
    }

    @Operation(summary = "查询风控规则详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:risk-rule:list")
    public Result<AdminRiskRuleVO> detail(@PathVariable Long id) {
        return Result.success(adminRiskRuleService.detail(id));
    }

    @Operation(summary = "新增风控规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "新增风控规则")
    @PostMapping
    @RequirePermission("admin:risk-rule:create")
    public Result<AdminRiskRuleVO> create(@Valid @RequestBody AdminRiskRuleDTO dto,
                                          HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRiskRuleService.create(dto, operatorId));
    }

    @Operation(summary = "更新风控规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "更新风控规则")
    @PutMapping("/{id}")
    @RequirePermission("admin:risk-rule:edit")
    public Result<AdminRiskRuleVO> update(@PathVariable Long id,
                                          @Valid @RequestBody AdminRiskRuleDTO dto,
                                          HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRiskRuleService.update(id, dto, operatorId));
    }

    @Operation(summary = "删除风控规则")
    @AuditAction(operationType = "UPDATE_PROFILE", description = "删除风控规则")
    @DeleteMapping("/{id}")
    @RequirePermission("admin:risk-rule:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminRiskRuleService.delete(id, operatorId);
        return Result.success(null);
    }

    @Operation(summary = "模拟风控规则链")
    @PostMapping("/simulate")
    @RequirePermission("admin:risk-rule:simulate")
    public Result<AdminRiskRuleSimulateVO> simulate(@Valid @RequestBody AdminRiskRuleSimulateDTO dto) {
        return Result.success(adminRiskRuleService.simulate(dto));
    }
}
