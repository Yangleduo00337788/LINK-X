package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceIdDTO;
import com.linkx.server.controller.dto.ConferenceJoinDTO;
import com.linkx.server.controller.dto.ConferenceMemberActionDTO;
import com.linkx.server.controller.dto.ConferenceMuteDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.dto.ConferenceTransferHostDTO;
import com.linkx.server.controller.dto.ConferenceVideoDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.service.ConferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/conference")
@RequiredArgsConstructor
public class ConferenceController {

    private final ConferenceService conferenceService;
    private final JwtUtils jwtUtils;

    @PostMapping("/create")
    public Result<ConferenceInfoVO> create(@Valid @RequestBody ConferenceCreateDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.create(userId, dto));
    }

    @PostMapping("/join")
    public Result<ConferenceInfoVO> join(@Valid @RequestBody ConferenceJoinDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.join(userId, dto.getConferenceId(), dto.getPassword()));
    }

    @PostMapping("/leave")
    public Result<Void> leave(@Valid @RequestBody ConferenceIdDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.leave(userId, dto.getConferenceId());
        return Result.success();
    }

    @PostMapping("/end")
    public Result<Void> end(@Valid @RequestBody ConferenceIdDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.end(userId, dto.getConferenceId());
        return Result.success();
    }

    @GetMapping("/info/{id}")
    public Result<ConferenceInfoVO> info(@PathVariable Long id, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.info(userId, id));
    }

    @GetMapping("/active")
    public Result<List<ConferenceInfoVO>> active(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(conferenceService.listActive(userId));
    }

    @PostMapping("/mute")
    public Result<Void> mute(@Valid @RequestBody ConferenceMuteDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long targetUserId = dto.getTargetUserId() != null ? dto.getTargetUserId() : userId;
        conferenceService.mute(userId, dto.getConferenceId(), targetUserId, Boolean.TRUE.equals(dto.getMuted()));
        return Result.success();
    }

    @PostMapping("/video")
    public Result<Void> video(@Valid @RequestBody ConferenceVideoDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.setVideo(userId, dto.getConferenceId(), Boolean.TRUE.equals(dto.getVideoOff()));
        return Result.success();
    }

    @PostMapping("/remove")
    public Result<Void> remove(@Valid @RequestBody ConferenceMemberActionDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.removeMember(userId, dto.getConferenceId(), dto.getTargetUserId());
        return Result.success();
    }

    @PostMapping("/transfer-host")
    public Result<Void> transferHost(@Valid @RequestBody ConferenceTransferHostDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.transferHost(userId, dto.getConferenceId(), dto.getNewHostId());
        return Result.success();
    }

    @PostMapping("/signal")
    public Result<Void> signal(@Valid @RequestBody ConferenceSignalDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.signal(userId, dto);
        return Result.success();
    }
}
