package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.MuteAllDTO;
import com.linkx.server.controller.dto.MuteMemberDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupRemarkDTO;
import com.linkx.server.controller.dto.UpdateMemberRoleDTO;
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
     * 设置或取消管理员（仅群主）
     */
    @PutMapping("/{conversationId}/members/{memberId}/role")
    public Result<Void> updateMemberRole(
            @PathVariable String conversationId,
            @PathVariable String memberId,
            @Valid @RequestBody UpdateMemberRoleDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.updateMemberRole(userId, parseId(conversationId), parseId(memberId), dto.getRole());
        return Result.success(null);
    }

    /**
     * 全体禁言 / 定时全体禁言（群主或管理员）
     */
    @PutMapping("/{conversationId}/mute-all")
    public Result<GroupConversationVO> updateMuteAll(
            @PathVariable String conversationId,
            @RequestBody MuteAllDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.updateMuteAll(userId, parseId(conversationId), dto));
    }

    /**
     * 指定成员禁言（群主或管理员）
     */
    @PutMapping("/{conversationId}/members/{memberId}/mute")
    public Result<Void> updateMemberMute(
            @PathVariable String conversationId,
            @PathVariable String memberId,
            @Valid @RequestBody MuteMemberDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.updateMemberMute(userId, parseId(conversationId), parseId(memberId), dto);
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

    // ==================== 群成员批量管理 ====================

    @PostMapping("/{conversationId}/members/batch-remove")
    public Result<Void> batchRemoveMembers(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, List<Long>> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.batchRemoveMembers(userId, parseId(conversationId), body.get("memberIds"));
        return Result.success(null);
    }

    @PostMapping("/{conversationId}/members/batch-mute")
    public Result<Void> batchMuteMembers(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        @SuppressWarnings("unchecked")
        List<Long> memberIds = ((List<Number>) body.get("memberIds")).stream().map(Number::longValue).toList();
        boolean muted = Boolean.TRUE.equals(body.get("muted"));
        groupService.batchMuteMembers(userId, parseId(conversationId), memberIds, muted);
        return Result.success(null);
    }

    // ==================== 入群审核 ====================

    @PostMapping("/{conversationId}/join-approval")
    public Result<Void> setJoinApproval(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, Boolean> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.setJoinApproval(userId, parseId(conversationId), Boolean.TRUE.equals(body.get("required")));
        return Result.success(null);
    }

    @PostMapping("/{conversationId}/join-request")
    public Result<Void> requestJoin(
            @PathVariable String conversationId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String message = body != null ? body.get("message") : null;
        groupService.requestJoin(userId, parseId(conversationId), message);
        return Result.success(null);
    }

    @PostMapping("/{conversationId}/join-request/{applicantId}")
    public Result<Void> handleJoinRequest(
            @PathVariable String conversationId,
            @PathVariable String applicantId,
            @RequestBody java.util.Map<String, Boolean> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.handleJoinRequest(userId, parseId(conversationId), parseId(applicantId), Boolean.TRUE.equals(body.get("approve")));
        return Result.success(null);
    }

    // ==================== 群公告已读统计 ====================

    @PostMapping("/{conversationId}/announcement/read")
    public Result<Void> markAnnouncementRead(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.markAnnouncementRead(userId, parseId(conversationId));
        return Result.success(null);
    }

    @GetMapping("/{conversationId}/announcement/read-count")
    public Result<Long> getAnnouncementReadCount(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(groupService.getAnnouncementReadCount(parseId(conversationId)));
    }

    // ==================== 群聊邀请策略 ====================

    @PostMapping("/{conversationId}/invite-policy")
    public Result<Void> setInvitePolicy(
            @PathVariable String conversationId,
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        groupService.setInvitePolicy(userId, parseId(conversationId), body.get("policy"));
        return Result.success(null);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
