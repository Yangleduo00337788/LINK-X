package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallIdDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.vo.CallInviteVO;
import com.linkx.server.service.CallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 语音/视频通话信令控制器
 */
@RestController
@RequestMapping("/call")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final JwtUtils jwtUtils;

    @PostMapping("/invite")
    public Result<CallInviteVO> invite(
            @Valid @RequestBody CallInviteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(callService.invite(userId, dto));
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(
            @Valid @RequestBody CallCancelDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.cancel(userId, dto);
        return Result.success(null);
    }

    @PostMapping("/accept")
    public Result<Void> accept(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.accept(userId, dto);
        return Result.success(null);
    }

    @PostMapping("/reject")
    public Result<Void> reject(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.reject(userId, dto);
        return Result.success(null);
    }

    @PostMapping("/hangup")
    public Result<Void> hangup(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.hangup(userId, dto);
        return Result.success(null);
    }

    /**
     * 中继 WebRTC SDP / ICE 到对端
     */
    @PostMapping("/signal")
    public Result<Void> signal(
            @Valid @RequestBody CallSignalDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.signal(userId, dto);
        return Result.success(null);
    }

    // ==================== 断线重连 ====================

    @PostMapping("/reconnect")
    public Result<Void> reconnect(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.reconnect(userId, body.get("callId"));
        return Result.success(null);
    }

    // ==================== 设备切换 ====================

    @PostMapping("/switch-device")
    public Result<Void> switchDevice(
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.switchDevice(userId, (String) body.get("callId"),
                (String) body.get("deviceType"), Boolean.TRUE.equals(body.get("enabled")));
        return Result.success(null);
    }

    // ==================== 多人会议 ====================

    @PostMapping("/conference/create")
    public Result<String> createConference(
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long conversationId = Long.parseLong(body.get("conversationId").toString());
        String callType = (String) body.getOrDefault("callType", "voice");
        return Result.success(callService.createConference(userId, conversationId, callType));
    }

    @PostMapping("/conference/join")
    public Result<Void> joinConference(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.joinConference(userId, body.get("callId"));
        return Result.success(null);
    }

    @PostMapping("/conference/leave")
    public Result<Void> leaveConference(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.leaveConference(userId, body.get("callId"));
        return Result.success(null);
    }

    @PostMapping("/conference/participants")
    public Result<java.util.List<java.util.Map<String, Object>>> getParticipants(
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(callService.getConferenceParticipants(userId, body.get("callId")));
    }
}
