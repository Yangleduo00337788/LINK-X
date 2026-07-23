package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.service.ComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据合规接口：导出与清除。
 */
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;
    private final JwtUtils jwtUtils;

    @GetMapping("/export")
    public Result<UserDataExportVO> export(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(complianceService.exportUserData(userId));
    }

    @PostMapping("/purge")
    public Result<Void> purge(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        complianceService.purgeUserData(userId);
        return Result.success();
    }
}
