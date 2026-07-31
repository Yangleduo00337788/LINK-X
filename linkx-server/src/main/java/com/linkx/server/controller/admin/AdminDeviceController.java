package com.linkx.server.controller.admin;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;
import com.linkx.server.service.admin.AdminDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-设备管理")
@RestController
@RequestMapping("/admin/devices")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin", "ops_admin", "audit_admin"})
public class AdminDeviceController {

    private final AdminDeviceService adminDeviceService;
    private final LinkxProperties linkxProperties;

    @Operation(summary = "查询设备会话列表")
    @GetMapping
    @RequirePermission("admin:device:list")
    public Result<PageResultVO<AdminDeviceVO>> list(@Valid AdminDeviceQueryDTO query) {
        return Result.success(adminDeviceService.list(query));
    }

    @Operation(summary = "强制设备下线", description = "吊销 token、断开连接并删除会话，写审计日志")
    @PostMapping("/{userId}/{deviceId}/kick")
    @RequirePermission("admin:device:kick")
    public Result<Void> kick(@PathVariable Long userId,
                             @PathVariable String deviceId,
                             HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        adminDeviceService.kick(
                userId,
                deviceId,
                operatorId,
                null,
                ClientIpResolver.resolve(request, linkxProperties),
                request.getHeader("User-Agent")
        );
        return Result.success(null);
    }
}
