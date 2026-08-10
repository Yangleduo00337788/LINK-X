package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.ConferenceAdmitDTO;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceIdDTO;
import com.linkx.server.controller.dto.ConferenceJoinDTO;
import com.linkx.server.controller.dto.ConferenceMemberActionDTO;
import com.linkx.server.controller.dto.ConferenceMuteDTO;
import com.linkx.server.controller.dto.ConferenceRaiseDTO;
import com.linkx.server.controller.dto.ConferenceSetRoleDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.dto.ConferenceTransferHostDTO;
import com.linkx.server.controller.dto.ConferenceVideoDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.service.ConferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "${openapi.tag.conference}")
@RequestMapping("/conference")
@RequiredArgsConstructor
@Validated
public class ConferenceController {

    private final ConferenceService conferenceService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "创建会议")
    @PostMapping("/create")
    public Result<ConferenceInfoVO> create(@Valid @RequestBody ConferenceCreateDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.create(userId, dto));
    }

    @Operation(summary = "加入会议")
    @PostMapping("/join")
    public Result<ConferenceInfoVO> join(@Valid @RequestBody ConferenceJoinDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.join(userId, dto.getConferenceId(), dto.getPassword()));
    }

    @Operation(summary = "离开会议")
    @PostMapping("/leave")
    public Result<Void> leave(@Valid @RequestBody ConferenceIdDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.leave(userId, dto.getConferenceId());
        return Result.success();
    }

    @Operation(summary = "结束会议")
    @PostMapping("/end")
    public Result<Void> end(@Valid @RequestBody ConferenceIdDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.end(userId, dto.getConferenceId());
        return Result.success();
    }

    @Operation(summary = "获取会议详情")
    @GetMapping("/info/{id}")
    public Result<ConferenceInfoVO> info(@PathVariable @Positive(message = "ID必须为正数") Long id, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.info(userId, id));
    }

    @Operation(summary = "获取进行中的会议列表")
    @GetMapping("/active")
    public Result<List<ConferenceInfoVO>> active(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.listActive(userId));
    }

    /** 会话内进行中会议（聊天顶栏条），无则 data=null */
    @Operation(summary = "获取会话内进行中会议")
    @GetMapping("/active-in-conversation")
    public Result<ConferenceInfoVO> activeInConversation(
            @org.springframework.web.bind.annotation.RequestParam Long conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.findActiveInConversation(userId, conversationId));
    }

    @Operation(summary = "设置会议静音")
    @PostMapping("/mute")
    public Result<Void> mute(@Valid @RequestBody ConferenceMuteDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long targetUserId = dto.getTargetUserId() != null ? dto.getTargetUserId() : userId;
        conferenceService.mute(userId, dto.getConferenceId(), targetUserId, Boolean.TRUE.equals(dto.getMuted()));
        return Result.success();
    }

    @Operation(summary = "设置会议视频开关")
    @PostMapping("/video")
    public Result<Void> video(@Valid @RequestBody ConferenceVideoDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.setVideo(userId, dto.getConferenceId(), Boolean.TRUE.equals(dto.getVideoOff()));
        return Result.success();
    }

    @Operation(summary = "移除会议成员")
    @PostMapping("/remove")
    public Result<Void> remove(@Valid @RequestBody ConferenceMemberActionDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.removeMember(userId, dto.getConferenceId(), dto.getTargetUserId());
        return Result.success();
    }

    @Operation(summary = "转让会议主持人")
    @PostMapping("/transfer-host")
    public Result<Void> transferHost(@Valid @RequestBody ConferenceTransferHostDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.transferHost(userId, dto.getConferenceId(), dto.getNewHostId());
        return Result.success();
    }

    @Operation(summary = "准许成员入会")
    @PostMapping("/admit")
    public Result<Void> admit(@Valid @RequestBody ConferenceAdmitDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.admitMember(userId, dto.getConferenceId(), dto.getTargetUserId());
        return Result.success();
    }

    @Operation(summary = "设置会议成员角色")
    @PostMapping("/set-role")
    public Result<Void> setRole(@Valid @RequestBody ConferenceSetRoleDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.setMemberRole(userId, dto.getConferenceId(), dto.getTargetUserId(), dto.getRole());
        return Result.success();
    }

    @Operation(summary = "举手/放下")
    @PostMapping("/raise")
    public Result<Void> raise(@Valid @RequestBody ConferenceRaiseDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.raiseHand(userId, dto.getConferenceId(), Boolean.TRUE.equals(dto.getRaised()));
        return Result.success();
    }

    @Operation(summary = "查询会议历史")
    @GetMapping("/history")
    public Result<List<ConferenceInfoVO>> history(
            @org.springframework.web.bind.annotation.RequestParam Long conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.listHistory(userId, conversationId));
    }

    @Operation(summary = "中继会议 WebRTC 信令")
    @PostMapping("/signal")
    @RateLimit(scope = "conference:signal", value = 20, window = 1)
    public Result<Void> signal(@Valid @RequestBody ConferenceSignalDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.signal(userId, dto);
        return Result.success();
    }
}
