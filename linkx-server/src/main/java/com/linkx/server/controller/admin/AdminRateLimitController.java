package com.linkx.server.controller.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.RequirePermission;
import com.linkx.server.common.RequireRole;
import com.linkx.server.common.RequireStepUp;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.admin.dto.AdminRateLimitUnblockDTO;
import com.linkx.server.controller.admin.dto.AdminRateLimitWhitelistDTO;
import com.linkx.server.controller.admin.vo.AdminRateLimitHitVO;
import com.linkx.server.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端-IP限流")
@RestController
@RequestMapping("/admin/rate-limits")
@RequiredArgsConstructor
@RequireRole(adminPortal = true)
public class AdminRateLimitController {

    private final RateLimitService rateLimitService;

    @Operation(summary = "扫描当前活跃限流计数")
    @GetMapping("/hits")
    @RequirePermission("admin:rate-limit:list")
    public Result<List<AdminRateLimitHitVO>> hits(
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(rateLimitService.listActiveHits(ip, limit));
    }

    @Operation(summary = "解除指定 IP 的限流计数")
    @PostMapping("/unblock")
    @RequirePermission("admin:rate-limit:unblock")
    @RequireStepUp("admin:rate-limit:unblock")
    @AuditAction(operationType = "RISK_EVENT_HANDLE", description = "解除 IP 限流", logParams = true)
    public Result<Map<String, Object>> unblock(@Valid @RequestBody AdminRateLimitUnblockDTO dto) {
        int deleted = rateLimitService.clearIpRateLimits(dto.getIp());
        return Result.success(Map.of("ip", dto.getIp().trim(), "deleted", deleted));
    }

    @Operation(summary = "限流白名单列表")
    @GetMapping("/whitelist")
    @RequirePermission("admin:rate-limit:list")
    public Result<List<String>> whitelist() {
        return Result.success(rateLimitService.listWhitelist());
    }

    @Operation(summary = "加入限流白名单")
    @PostMapping("/whitelist")
    @RequirePermission("admin:rate-limit:whitelist")
    @RequireStepUp("admin:rate-limit:whitelist")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "加入限流白名单", logParams = true)
    public Result<Void> addWhitelist(@Valid @RequestBody AdminRateLimitWhitelistDTO dto) {
        rateLimitService.addWhitelist(dto.getIp());
        return Result.success(null);
    }

    @Operation(summary = "移出限流白名单")
    @DeleteMapping("/whitelist")
    @RequirePermission("admin:rate-limit:whitelist")
    @RequireStepUp("admin:rate-limit:whitelist")
    @AuditAction(operationType = "UPDATE_SETTINGS", description = "移出限流白名单", logParams = true)
    public Result<Void> removeWhitelist(@RequestParam String ip) {
        rateLimitService.removeWhitelist(ip);
        return Result.success(null);
    }
}
