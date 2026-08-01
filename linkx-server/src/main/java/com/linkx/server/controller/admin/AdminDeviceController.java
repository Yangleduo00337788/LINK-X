package com.linkx.server.controller.admin;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.Result;
import com.linkx.server.common.admin.AdminCsvResponses;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端-设备管理")
@RestController
@RequestMapping("/admin/devices")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminDeviceController {

    private final AdminDeviceService adminDeviceService;
    private final LinkxProperties linkxProperties;

    @Operation(summary = "查询设备会话列表")
    @GetMapping
    @RequirePermission("admin:device:list")
    public Result<PageResultVO<AdminDeviceVO>> list(@Valid AdminDeviceQueryDTO query) {
        return Result.success(adminDeviceService.list(query));
    }

    @Operation(summary = "导出设备会话 CSV")
    @GetMapping("/export")
    @RequirePermission("admin:device:export")
    public ResponseEntity<byte[]> export(@Valid AdminDeviceQueryDTO query) {
        List<AdminDeviceVO> items = adminDeviceService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminDeviceVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getDeviceId()),
                    AdminCsvResponses.cell(item.getDeviceName()),
                    AdminCsvResponses.cell(item.getDeviceType()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(Boolean.TRUE.equals(item.getOnline()) ? "online" : "offline"),
                    AdminCsvResponses.cell(item.getLastActive()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return AdminCsvResponses.csv("devices",
                List.of("id", "userId", "username", "nickname", "deviceId", "deviceName",
                        "deviceType", "ip", "online", "lastActive", "createTime"),
                rows);
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
