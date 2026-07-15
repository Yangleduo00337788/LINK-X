package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserSearchVO;
import com.linkx.server.service.FriendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final JwtUtils jwtUtils;

    @GetMapping("/search")
    @RateLimit(scope = "friend:search", value = 30, window = 60)
    public Result<List<UserSearchVO>> searchUsers(
            @RequestParam String keyword,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.searchUsers(keyword, userId));
    }

    @PostMapping("/request")
    public Result<Void> sendFriendRequest(
            @Valid @RequestBody SendFriendRequestDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.sendFriendRequest(userId, dto);
        return Result.success(null);
    }

    @GetMapping("/requests/incoming")
    public Result<List<FriendRequestVO>> listIncomingRequests(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listIncomingRequests(userId));
    }

    @GetMapping("/requests/outgoing")
    public Result<List<FriendRequestVO>> listOutgoingRequests(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listOutgoingRequests(userId));
    }

    @PostMapping("/requests/{id}/accept")
    public Result<Void> acceptFriendRequest(
            @PathVariable String id,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.acceptFriendRequest(userId, parseRequestId(id));
        return Result.success(null);
    }

    @PostMapping("/requests/{id}/reject")
    public Result<Void> rejectFriendRequest(
            @PathVariable String id,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.rejectFriendRequest(userId, parseRequestId(id));
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<FriendItemVO>> listFriends(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listFriends(userId));
    }

    @DeleteMapping("/{friendId}")
    public Result<Void> deleteFriend(
            @PathVariable String friendId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.deleteFriend(userId, parseRequestId(friendId));
        return Result.success(null);
    }

    private Long parseRequestId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的申请 ID");
        }
    }
}
