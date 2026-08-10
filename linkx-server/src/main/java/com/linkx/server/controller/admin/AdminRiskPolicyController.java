package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminRiskPolicySimulateDTO;
import com.linkx.server.controller.admin.dto.AdminRiskPolicyUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicySimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicyVO;
import com.linkx.server.service.admin.AdminRiskPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-风控策略")
@RestController
@RequestMapping("/admin/risk-policies")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRiskPolicyController {

    private final AdminRiskPolicyService adminRiskPolicyService;

    @Operation(summary = "风控策略总览")
    @GetMapping
    @RequirePermission("admin:risk-policy:list")
    public Result<AdminRiskPolicyVO> overview() {
        return Result.success(adminRiskPolicyService.getOverview());
    }

    @Operation(summary = "更新风控策略阈值")
    @AuditAction(operationType = "UPDATE_RISK_POLICY", description = "更新风控策略")
    @PutMapping
    @RequirePermission("admin:risk-policy:edit")
    public Result<AdminRiskPolicyVO> update(@Valid @RequestBody AdminRiskPolicyUpdateDTO dto,
                                            HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRiskPolicyService.update(dto, operatorId));
    }

    @Operation(summary = "风控命中模拟")
    @PostMapping("/simulate")
    @RequirePermission("admin:risk-policy:edit")
    public Result<AdminRiskPolicySimulateVO> simulate(@Valid @RequestBody AdminRiskPolicySimulateDTO dto) {
        return Result.success(adminRiskPolicyService.simulate(dto));
    }
}
