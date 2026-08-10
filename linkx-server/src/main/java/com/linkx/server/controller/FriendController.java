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
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.UpdateFriendGroupDTO;
import com.linkx.server.controller.dto.UpdateFriendRemarkDTO;
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
@Tag(name = "${openapi.tag.friend}")
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "搜索用户")
    @GetMapping("/search")
    @RateLimit(scope = "friend:search", value = 30, window = 60)
    public Result<List<UserSearchVO>> searchUsers(
            @RequestParam String keyword,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.searchUsers(keyword, userId));
    }

    @Operation(summary = "发送好友申请")
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(
            @Valid @RequestBody SendFriendRequestDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.sendFriendRequest(userId, dto);
        return Result.success(null);
    }

    @Operation(summary = "获取收到的好友申请")
    @GetMapping("/requests/incoming")
    public Result<List<FriendRequestVO>> listIncomingRequests(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listIncomingRequests(userId));
    }

    @Operation(summary = "获取发出的好友申请")
    @GetMapping("/requests/outgoing")
    public Result<List<FriendRequestVO>> listOutgoingRequests(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listOutgoingRequests(userId));
    }

    @Operation(summary = "接受好友申请")
    @PostMapping("/requests/{id}/accept")
    public Result<Void> acceptFriendRequest(
            @PathVariable String id,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.acceptFriendRequest(userId, parseRequestId(id));
        return Result.success(null);
    }

    @Operation(summary = "拒绝好友申请")
    @PostMapping("/requests/{id}/reject")
    public Result<Void> rejectFriendRequest(
            @PathVariable String id,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.rejectFriendRequest(userId, parseRequestId(id));
        return Result.success(null);
    }

    @Operation(summary = "清空已处理的好友申请")
    @DeleteMapping("/requests/clear")
    public Result<Integer> clearProcessedFriendRequests(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        int cleared = friendService.clearProcessedFriendRequests(userId);
        return Result.success(cleared);
    }

    @Operation(summary = "获取好友列表")
    @GetMapping("/list")
    public Result<List<FriendItemVO>> listFriends(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(friendService.listFriends(userId));
    }

    @Operation(summary = "删除好友")
    @DeleteMapping("/{friendId}")
    public Result<Void> deleteFriend(
            @PathVariable String friendId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.deleteFriend(userId, parseRequestId(friendId));
        return Result.success(null);
    }

    @Operation(summary = "修改好友备注")
    @PutMapping("/{friendId}/remark")
    public Result<String> updateFriendRemark(
            @PathVariable String friendId,
            @Valid @RequestBody UpdateFriendRemarkDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String remark = friendService.updateFriendRemark(userId, parseRequestId(friendId), dto.getRemark());
        return Result.success(remark);
    }

    @Operation(summary = "修改好友分组")
    @PutMapping("/{friendId}/group")
    public Result<String> updateFriendGroup(
            @PathVariable String friendId,
            @Valid @RequestBody UpdateFriendGroupDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String groupName = friendService.updateFriendGroup(userId, parseRequestId(friendId), dto.getGroupName());
        return Result.success(groupName);
    }

    @Operation(summary = "拉黑好友")
    @PostMapping("/{friendId}/block")
    public Result<Void> blockFriend(
            @PathVariable String friendId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.blockFriend(userId, parseRequestId(friendId));
        return Result.success(null);
    }

    @Operation(summary = "取消拉黑好友")
    @PostMapping("/{friendId}/unblock")
    public Result<Void> unblockFriend(
            @PathVariable String friendId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        friendService.unblockFriend(userId, parseRequestId(friendId));
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
