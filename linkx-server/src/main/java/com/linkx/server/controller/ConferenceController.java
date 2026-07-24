package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
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
import java.util.Map;

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
    public Result<ConferenceInfoVO> join(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long conferenceId = Long.parseLong(body.get("conferenceId").toString());
        String password = body.get("password") != null ? body.get("password").toString() : null;
        return Result.success(conferenceService.join(userId, conferenceId, password));
    }

    @PostMapping("/leave")
    public Result<Void> leave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.leave(userId, Long.parseLong(body.get("conferenceId").toString()));
        return Result.success();
    }

    @PostMapping("/end")
    public Result<Void> end(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.end(userId, Long.parseLong(body.get("conferenceId").toString()));
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
    public Result<Void> mute(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long conferenceId = Long.parseLong(body.get("conferenceId").toString());
        Long targetUserId = body.get("targetUserId") != null
                ? Long.parseLong(body.get("targetUserId").toString()) : userId;
        boolean muted = Boolean.TRUE.equals(body.get("muted")) || "true".equals(String.valueOf(body.get("muted")));
        conferenceService.mute(userId, conferenceId, targetUserId, muted);
        return Result.success();
    }

    @PostMapping("/video")
    public Result<Void> video(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long conferenceId = Long.parseLong(body.get("conferenceId").toString());
        boolean videoOff = Boolean.TRUE.equals(body.get("videoOff")) || "true".equals(String.valueOf(body.get("videoOff")));
        conferenceService.setVideo(userId, conferenceId, videoOff);
        return Result.success();
    }

    @PostMapping("/remove")
    public Result<Void> remove(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.removeMember(userId,
                Long.parseLong(body.get("conferenceId").toString()),
                Long.parseLong(body.get("targetUserId").toString()));
        return Result.success();
    }

    @PostMapping("/transfer-host")
    public Result<Void> transferHost(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.transferHost(userId,
                Long.parseLong(body.get("conferenceId").toString()),
                Long.parseLong(body.get("newHostId").toString()));
        return Result.success();
    }

    @PostMapping("/signal")
    public Result<Void> signal(@Valid @RequestBody ConferenceSignalDTO dto, HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        conferenceService.signal(userId, dto);
        return Result.success();
    }
}
