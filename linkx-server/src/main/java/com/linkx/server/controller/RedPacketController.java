package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.config.aspect.AuditAction;
import com.linkx.server.controller.dto.SendRedPacketDTO;
import com.linkx.server.controller.vo.RedPacketVO;
import com.linkx.server.service.RedPacketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 红包控制器
 */
@RestController
@RequestMapping("/red-packet")
@RequiredArgsConstructor
public class RedPacketController {

    private final RedPacketService redPacketService;
    private final JwtUtils jwtUtils;

    /**
     * 发送红包
     */
    @PostMapping
    @RateLimit(scope = "red-packet:send", value = 10, window = 60)
    @AuditAction(operationType = "RED_PACKET", description = "发送红包")
    public Result<RedPacketVO> sendRedPacket(
            @Valid @RequestBody SendRedPacketDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(redPacketService.sendRedPacket(userId, dto));
    }

    /**
     * 领取红包
     */
    @PostMapping("/{redPacketId}/receive")
    @RateLimit(scope = "red-packet:receive", value = 30, window = 60)
    @AuditAction(operationType = "RED_PACKET", description = "领取红包")
    public Result<RedPacketVO> receiveRedPacket(
            @PathVariable String redPacketId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(redPacketService.receiveRedPacket(userId, redPacketId));
    }

    /**
     * 获取红包详情
     */
    @GetMapping("/{redPacketId}")
    @RateLimit(scope = "red-packet:detail", value = 60, window = 60)
    public Result<RedPacketVO> getRedPacket(
            @PathVariable String redPacketId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(redPacketService.getRedPacket(userId, redPacketId));
    }

    /**
     * 获取会话中的红包列表
     */
    @GetMapping("/conversation/{conversationId}")
    @RateLimit(scope = "red-packet:list", value = 60, window = 60)
    public Result<List<RedPacketVO>> listByConversation(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(redPacketService.listByConversation(userId, parseId(conversationId)));
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的ID");
        }
    }
}
