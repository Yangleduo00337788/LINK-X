package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.dto.AdminBiQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBiMetricVO;
import com.linkx.server.controller.admin.vo.AdminBiQueryVO;
import com.linkx.server.controller.admin.vo.AdminBigScreenVO;
import com.linkx.server.service.admin.AdminBiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-高级 BI")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminBiController {

    private final AdminBiService adminBiService;

    @Operation(summary = "BI 指标目录")
    @GetMapping("/bi/metrics")
    @RequirePermission("admin:bi:view")
    public Result<List<AdminBiMetricVO>> metrics() {
        return Result.success(adminBiService.listMetrics());
    }

    @Operation(summary = "BI 高级查询（维度/对比/下钻）")
    @PostMapping("/bi/query")
    @RequirePermission("admin:bi:view")
    public Result<AdminBiQueryVO> query(@Valid @RequestBody AdminBiQueryDTO dto) {
        return Result.success(adminBiService.query(dto));
    }

    @Operation(summary = "实时大屏数据")
    @GetMapping("/big-screen/data")
    @RequirePermission("admin:big-screen:view")
    public Result<AdminBigScreenVO> bigScreenData() {
        return Result.success(adminBiService.bigScreenData());
    }
}
