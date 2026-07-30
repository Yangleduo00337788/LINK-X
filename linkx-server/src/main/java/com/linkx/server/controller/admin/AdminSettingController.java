package com.linkx.server.controller.admin;

import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.controller.admin.vo.AdminSettingVO;
import com.linkx.server.service.admin.AdminSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-系统配置")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminSettingController {

    private final AdminSettingService adminSettingService;

    @Operation(summary = "查询系统配置")
    @GetMapping
    @RequirePermission("admin:setting:view")
    public Result<AdminSettingVO> get() {
        return Result.success(adminSettingService.getSettings());
    }
}
