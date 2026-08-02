package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminRiskEventBatchDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;
import com.linkx.server.service.admin.AdminRiskEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-风险事件")
@RestController
@RequestMapping("/admin/risk-events")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRiskEventController {

    private final AdminRiskEventService adminRiskEventService;

    @Operation(summary = "查询风险事件列表")
    @GetMapping
    @RequirePermission("admin:risk-event:list")
    public Result<PageResultVO<AdminRiskEventVO>> list(@Valid AdminRiskEventQueryDTO query) {
        return Result.success(adminRiskEventService.list(query));
    }

    @Operation(summary = "导出风险事件 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:risk-event:export")
    public ResponseEntity<byte[]> export(@Valid AdminRiskEventQueryDTO query) {
        List<AdminRiskEventVO> items = adminRiskEventService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminRiskEventVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getEventType()),
                    AdminCsvResponses.cell(item.getTitle()),
                    AdminCsvResponses.cell(item.getRiskLevel()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getRegion()),
                    AdminCsvResponses.cell(item.getResolution()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("risk-events",
                List.of("id", "eventType", "title", "riskLevel", "status", "username", "ip", "region", "resolution", "createTime"),
                rows);
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

    @Operation(summary = "批量处置风险事件")
    @AuditAction(operationType = "RISK_EVENT_BATCH", description = "批量处置风险事件")
    @PostMapping("/batch")
    @RequirePermission("admin:risk-event:batch")
    public Result<AdminReviewBatchResultVO> batch(@Valid @RequestBody AdminRiskEventBatchDTO dto,
                                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.success(adminRiskEventService.batch(dto, operatorId));
    }
}
