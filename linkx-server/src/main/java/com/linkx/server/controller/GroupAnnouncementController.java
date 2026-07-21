package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.CreateGroupAnnouncementDTO;
import com.linkx.server.controller.dto.UpdateGroupAnnouncementDTO;
import com.linkx.server.controller.vo.GroupAnnouncementVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.GroupAnnouncementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group/{conversationId}/announcements")
@RequiredArgsConstructor
public class GroupAnnouncementController {

    private final GroupAnnouncementService announcementService;
    private final JwtUtils jwtUtils;

    @GetMapping
    public Result<List<GroupAnnouncementVO>> list(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(announcementService.list(userId, parseId(conversationId)));
    }

    /** 侧栏/抽屉摘要：置顶优先，否则最新 */
    @GetMapping("/display")
    public Result<GroupAnnouncementVO> display(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(announcementService.display(userId, parseId(conversationId)));
    }

    @PostMapping
    public Result<GroupAnnouncementVO> create(
            @PathVariable String conversationId,
            @Valid @RequestBody CreateGroupAnnouncementDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(announcementService.create(userId, parseId(conversationId), dto));
    }

    @PutMapping("/{announcementId}")
    public Result<GroupAnnouncementVO> update(
            @PathVariable String conversationId,
            @PathVariable String announcementId,
            @Valid @RequestBody UpdateGroupAnnouncementDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(announcementService.update(
                userId, parseId(conversationId), parseId(announcementId), dto));
    }

    @DeleteMapping("/{announcementId}")
    public Result<Void> delete(
            @PathVariable String conversationId,
            @PathVariable String announcementId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        announcementService.delete(userId, parseId(conversationId), parseId(announcementId));
        return Result.success(null);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "无效的 ID");
        }
    }
}
