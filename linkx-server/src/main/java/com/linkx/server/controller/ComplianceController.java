package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CompliancePurgeDTO;
import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.service.ComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据合规接口：导出与清除。
 * 审计由 ComplianceService 记录（含 purge 密码失败），避免与 @AuditAction 重复落库。
 */
@RestController
@Tag(name = "${openapi.tag.compliance}")
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "导出个人数据")
    @GetMapping("/export")
    @RateLimit(scope = "compliance:export", value = 5, window = 60)
    public Result<UserDataExportVO> export(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(complianceService.exportUserData(userId));
    }

    @Operation(summary = "清除个人数据")
    @PostMapping("/purge")
    @RateLimit(scope = "compliance:purge", value = 3, window = 300)
    public Result<Void> purge(
            @Valid @RequestBody CompliancePurgeDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        complianceService.purgeUserData(userId, dto.getPassword());
        return Result.success();
    }
}
