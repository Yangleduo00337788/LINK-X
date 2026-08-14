package com.linkx.server.controller;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.LinkMateChatDTO;
import com.linkx.server.controller.dto.LinkMateGroupReplyDTO;
import com.linkx.server.controller.dto.LinkMateSessionRenameDTO;
import com.linkx.server.controller.vo.LinkMateMessageVO;
import com.linkx.server.controller.vo.LinkMateSessionVO;
import com.linkx.server.controller.vo.LinkMateStatusVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.service.LinkMateService;
import com.linkx.server.service.linkmate.LinkMateConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 灵伴（LinkMate）AI 助手接口。
 */
@RestController
@Tag(name = "${openapi.tag.linkmate}")
@RequestMapping("/linkmate")
@RequiredArgsConstructor
public class LinkMateController {

    private final LinkMateService linkMateService;
    private final ImMessagePushService imMessagePushService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "灵伴服务状态")
    @GetMapping("/status")
    public Result<LinkMateStatusVO> status(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(linkMateService.status(userId));
    }

    @Operation(summary = "对话列表")
    @GetMapping("/sessions")
    public Result<List<LinkMateSessionVO>> listSessions(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(linkMateService.listSessions(userId));
    }

    @Operation(summary = "新建对话")
    @PostMapping("/sessions")
    public Result<LinkMateSessionVO> createSession(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(linkMateService.createSession(userId));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        linkMateService.deleteSession(userId, parseId(sessionId));
        return Result.success(null);
    }

    @Operation(summary = "重命名对话")
    @PatchMapping("/sessions/{sessionId}")
    public Result<LinkMateSessionVO> renameSession(
            @PathVariable String sessionId,
            @Valid @RequestBody LinkMateSessionRenameDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(linkMateService.renameSession(userId, parseId(sessionId), dto.getTitle()));
    }

    @Operation(summary = "对话消息历史（分页）")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<LinkMateMessageVO>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "limit 必须 ≥1") @Max(value = 100, message = "limit 必须 ≤100") int limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long beforeId = before != null && !before.isBlank() ? parseId(before) : null;
        return Result.success(linkMateService.listMessages(userId, parseId(sessionId), beforeId, limit));
    }

    @Operation(summary = "发送消息（非流式）")
    @PostMapping("/chat")
    @RateLimit(scope = "linkmate:chat", value = 30, window = 60)
    public Result<LinkMateMessageVO> chat(
            @Valid @RequestBody LinkMateChatDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(linkMateService.chat(userId, dto));
    }

    @Operation(summary = "发送消息（SSE 流式）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(scope = "linkmate:chat", value = 30, window = 60)
    public SseEmitter streamChat(
            @Valid @RequestBody LinkMateChatDTO dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return linkMateService.streamChat(userId, dto);
    }

    @Operation(summary = "群聊/单聊 @灵伴 回复（消息落入 IM 时间线）")
    @PostMapping("/group/reply")
    @RateLimit(scope = "linkmate:group", value = 20, window = 60)
    public Result<MessageVO> replyInGroup(
            @Valid @RequestBody LinkMateGroupReplyDTO dto,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        MessageVO vo = linkMateService.replyInImChat(userId, dto);
        imMessagePushService.pushToConversationMembers(vo, LinkMateConstants.BOT_SENDER_ID, null);
        return Result.success(vo);
    }

    @Operation(summary = "群聊/单聊 @灵伴 回复（SSE 流式，完成后落入 IM 时间线）")
    @PostMapping(value = "/group/reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(scope = "linkmate:group", value = 20, window = 60)
    public SseEmitter streamReplyInGroup(
            @Valid @RequestBody LinkMateGroupReplyDTO dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return linkMateService.streamReplyInImChat(userId, dto);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "invalid id");
        }
    }
}
