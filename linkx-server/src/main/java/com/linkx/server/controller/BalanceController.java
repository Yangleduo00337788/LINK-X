package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.RechargeDTO;
import com.linkx.server.controller.vo.BalanceLogVO;
import com.linkx.server.controller.vo.BalanceVO;
import com.linkx.server.entity.BalanceLog;
import com.linkx.server.service.BalanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 余额控制器
 */
@RestController
@Tag(name = "${openapi.tag.balance}")
@RequestMapping("/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;
    private final JwtUtils jwtUtils;

    /**
     * 获取当前用户余额
     */
    @GetMapping
    public Result<BalanceVO> getBalance(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(balanceService.getBalance(userId));
    }

    /**
     * 余额流水（本人）
     */
    @GetMapping("/logs")
    public Result<List<BalanceLogVO>> listLogs(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long beforeId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(balanceService.listLogs(userId, limit, beforeId));
    }

    /**
     * 演示充值（无真实支付网关；限流防刷）
     */
    @PostMapping("/recharge")
    @RateLimit(scope = "balance:recharge", value = 10, window = 60)
    public Result<BalanceVO> recharge(
            @Valid @RequestBody RechargeDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String bizId = "recharge-" + UUID.randomUUID();
        balanceService.addBalance(
                userId,
                dto.getAmount(),
                BalanceLog.TYPE_RECHARGE,
                bizId,
                "账户充值");
        return Result.success(balanceService.getBalance(userId));
    }
}
