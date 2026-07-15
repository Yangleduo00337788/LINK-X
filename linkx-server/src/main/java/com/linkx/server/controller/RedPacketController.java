package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
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
