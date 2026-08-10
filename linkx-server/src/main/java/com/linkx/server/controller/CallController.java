package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallConferenceCreateDTO;
import com.linkx.server.controller.dto.CallIdDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.CallSignalDTO;
import com.linkx.server.controller.dto.CallSwitchDeviceDTO;
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
@Tag(name = "${openapi.tag.call}")
@RequestMapping("/call")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "发起通话邀请")
    @PostMapping("/invite")
    public Result<CallInviteVO> invite(
            @Valid @RequestBody CallInviteDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(callService.invite(userId, dto));
    }

    @Operation(summary = "取消通话邀请")
    @PostMapping("/cancel")
    public Result<Void> cancel(
            @Valid @RequestBody CallCancelDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.cancel(userId, dto);
        return Result.success(null);
    }

    @Operation(summary = "接听通话")
    @PostMapping("/accept")
    public Result<Void> accept(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.accept(userId, dto);
        return Result.success(null);
    }

    @Operation(summary = "拒绝通话")
    @PostMapping("/reject")
    public Result<Void> reject(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.reject(userId, dto);
        return Result.success(null);
    }

    @Operation(summary = "挂断通话")
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
    @Operation(summary = "中继 WebRTC 信令")
    @PostMapping("/signal")
    public Result<Void> signal(
            @Valid @RequestBody CallSignalDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.signal(userId, dto);
        return Result.success(null);
    }

    @Operation(summary = "通话重连")
    @PostMapping("/reconnect")
    public Result<Void> reconnect(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.reconnect(userId, dto.getCallId());
        return Result.success(null);
    }

    @Operation(summary = "切换通话设备")
    @PostMapping("/switch-device")
    public Result<Void> switchDevice(
            @Valid @RequestBody CallSwitchDeviceDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.switchDevice(userId, dto.getCallId(), dto.getDeviceType(), Boolean.TRUE.equals(dto.getEnabled()));
        return Result.success(null);
    }

    @Operation(summary = "创建会议通话")
    @PostMapping("/conference/create")
    public Result<String> createConference(
            @Valid @RequestBody CallConferenceCreateDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String callType = dto.getCallType() != null ? dto.getCallType() : "voice";
        return Result.success(callService.createConference(userId, dto.getConversationId(), callType));
    }

    @Operation(summary = "加入会议通话")
    @PostMapping("/conference/join")
    public Result<Void> joinConference(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.joinConference(userId, dto.getCallId());
        return Result.success(null);
    }

    @Operation(summary = "离开会议通话")
    @PostMapping("/conference/leave")
    public Result<Void> leaveConference(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        callService.leaveConference(userId, dto.getCallId());
        return Result.success(null);
    }

    @Operation(summary = "查询会议参与者")
    @PostMapping("/conference/participants")
    public Result<java.util.List<java.util.Map<String, Object>>> getParticipants(
            @Valid @RequestBody CallIdDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(callService.getConferenceParticipants(userId, dto.getCallId()));
    }
}
