package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;
import com.linkx.server.service.admin.AdminRiskEventService;
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

@Tag(name = "管理端-风险事件")
@RestController
@RequestMapping("/admin/risk-events")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminRiskEventController {

    private final AdminRiskEventService adminRiskEventService;

    @Operation(summary = "查询风险事件列表")
    @GetMapping
    @RequirePermission("admin:risk-event:list")
    public Result<PageResultVO<AdminRiskEventVO>> list(@Valid AdminRiskEventQueryDTO query) {
        return Result.success(adminRiskEventService.list(query));
    }

    @Operation(summary = "查询风险事件详情")
    @GetMapping("/{id}")
    @RequirePermission("admin:risk-event:list")
    public Result<AdminRiskEventVO> detail(@PathVariable Long id) {
        return Result.success(adminRiskEventService.detail(id));
    }

    @Operation(summary = "处置风险事件")
    @AuditAction(operationType = "RISK_EVENT_HANDLE", description = "处置风险事件")
    @PostMapping("/{id}/handle")
    @RequirePermission("admin:risk-event:handle")
    public Result<Void> handle(@PathVariable Long id,
                               @Valid @RequestBody AdminRiskEventHandleDTO dto,
                               HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminRiskEventService.handle(id, dto, operatorId);
        return Result.success(null);
    }
}
