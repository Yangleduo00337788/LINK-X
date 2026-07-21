package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupRemarkDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群聊控制器
 */
@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final JwtUtils jwtUtils;

    /**
     * 创建群聊
     */
    @PostMapping
    public Result<GroupConversationVO> createGroup(
            @Valid @RequestBody CreateGroupDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.createGroup(userId, dto));
    }

    /**
     * 获取我的群聊列表
     */
    @GetMapping("/list")
    public Result<List<ConversationVO>> listGroups(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.listGroups(userId));
    }

    /**
     * 获取群详情
     */
    @GetMapping("/{conversationId}/info")
    public Result<GroupConversationVO> getGroupInfo(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.getGroupInfo(userId, parseId(conversationId)));
    }

    /**
     * 更新群信息
     */
    @PutMapping("/{conversationId}")
    public Result<GroupConversationVO> updateGroup(
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateGroupDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.updateGroup(userId, parseId(conversationId), dto));
    }

    /**
     * 获取群成员列表
     */
    @GetMapping("/{conversationId}/members")
    public Result<List<GroupMemberVO>> listMembers(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.listMembers(userId, parseId(conversationId)));
    }

    /**
     * 添加群成员
     */
    @PostMapping("/{conversationId}/members")
    public Result<List<GroupMemberVO>> addMembers(
            @PathVariable String conversationId,
            @Valid @RequestBody AddGroupMembersDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.addMembers(userId, parseId(conversationId), dto));
    }

    /**
     * 移除群成员
     */
    @DeleteMapping("/{conversationId}/members/{memberId}")
    public Result<Void> removeMember(
            @PathVariable String conversationId,
            @PathVariable String memberId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.removeMember(userId, parseId(conversationId), parseId(memberId));
        return Result.success(null);
    }

    /**
     * 退出群聊
     */
    @PostMapping("/{conversationId}/quit")
    public Result<Void> quitGroup(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.quitGroup(userId, parseId(conversationId));
        return Result.success(null);
    }

    /**
     * 解散群聊
     */
    @DeleteMapping("/{conversationId}")
    public Result<Void> dissolveGroup(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.dissolveGroup(userId, parseId(conversationId));
        return Result.success(null);
    }

    /**
     * 转让群主
     */
    @PostMapping("/{conversationId}/transfer")
    public Result<Void> transferOwner(
            @PathVariable String conversationId,
            @RequestParam Long newOwnerId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.transferOwner(userId, parseId(conversationId), newOwnerId);
        return Result.success(null);
    }

    /**
     * 更新当前用户对本群的备注
     */
    @PutMapping("/{conversationId}/remark")
    public Result<String> updateMyRemark(
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateGroupRemarkDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.updateMyRemark(userId, parseId(conversationId), dto.getRemark()));
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
