package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminAbnormalAccessQueryDTO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessSummaryVO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessVO;
import com.linkx.server.service.admin.AdminAbnormalAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-异常访问")
@RestController
@RequestMapping("/admin/abnormal-access")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminAbnormalAccessController {

    private final AdminAbnormalAccessService adminAbnormalAccessService;

    @Operation(summary = "异常访问概览")
    @GetMapping("/summary")
    @RequirePermission("admin:abnormal-access:list")
    public Result<AdminAbnormalAccessSummaryVO> summary() {
        return Result.success(adminAbnormalAccessService.summary());
    }

    @Operation(summary = "查询异常访问记录")
    @GetMapping
    @RequirePermission("admin:abnormal-access:list")
    public Result<PageResultVO<AdminAbnormalAccessVO>> list(@Valid AdminAbnormalAccessQueryDTO query) {
        return Result.success(adminAbnormalAccessService.list(query));
    }

    @Operation(summary = "导出异常访问 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:abnormal-access:export")
    public ResponseEntity<byte[]> export(@Valid AdminAbnormalAccessQueryDTO query) {
        List<AdminAbnormalAccessVO> items = adminAbnormalAccessService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminAbnormalAccessVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getSource()),
                    AdminCsvResponses.cell(item.getSourceId()),
                    AdminCsvResponses.cell(item.getCategory()),
                    AdminCsvResponses.cell(item.getTitle()),
                    AdminCsvResponses.cell(item.getDetail()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getRegion()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getIdentity()),
                    AdminCsvResponses.cell(item.getHitCount()),
                    AdminCsvResponses.cell(item.getRiskLevel()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getOccurredAt()),
            });
        }
        return AdminCsvResponses.csv("abnormal-access",
                List.of("source", "sourceId", "category", "title", "detail", "ip", "region",
                        "username", "identity", "hitCount", "riskLevel", "status", "occurredAt"),
                rows);
    }
}
