package com.linkx.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.InviteGroupDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupInvitationVO;
import com.linkx.server.service.GroupInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群邀请控制器。
 * <p>
 * - 邀请：{@code POST /group/{conversationId}/invitations}
 * - 我的邀请：{@code GET /group/invitations}
 * - 接受：{@code POST /group/invitations/{invitationId}/accept}
 * - 拒绝：{@code POST /group/invitations/{invitationId}/reject}
 * </p>
 */
@RestController
@Tag(name = "${openapi.tag.group-invitation}")
@RequestMapping("/group/invitations")
@RequiredArgsConstructor
public class GroupInvitationController {

    private final GroupInvitationService invitationService;
    private final JwtUtils jwtUtils;

    /**
     * 当前用户收到的群邀请列表。
     */
    @Operation(summary = "获取我的群邀请列表")
    @GetMapping
    public Result<List<GroupInvitationVO>> list(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(invitationService.listMyInvitations(userId));
    }

    /**
     * 接受邀请：写入会话成员表，返回新会话信息（前端可直接打开该群会话）。
     */
    @Operation(summary = "接受群邀请")
    @PostMapping("/{invitationId}/accept")
    public Result<GroupConversationVO> accept(
            @PathVariable String invitationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(invitationService.accept(userId, parseId(invitationId)));
    }

    /**
     * 拒绝邀请。
     */
    @Operation(summary = "拒绝群邀请")
    @PostMapping("/{invitationId}/reject")
    public Result<Void> reject(
            @PathVariable String invitationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        invitationService.reject(userId, parseId(invitationId));
        return Result.success(null);
    }

    @Operation(summary = "清空已处理的群邀请")
    @DeleteMapping("/clear")
    public Result<Integer> clearProcessed(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        int cleared = invitationService.clearProcessedInvitations(userId);
        return Result.success(cleared);
    }

    /**
     * 群成员邀请新成员入群（owner/admin/member 均可作为邀请人）。
     */
    @Operation(summary = "邀请用户入群")
    @PostMapping("/{conversationId}")
    public Result<GroupInvitationVO> invite(
            @PathVariable String conversationId,
            @Valid @RequestBody InviteGroupDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(
                invitationService.invite(userId, parseId(conversationId), dto)
        );
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
