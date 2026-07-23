package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.MediaStreamResponses;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ChatSearchHitVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.ConversationDraftService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ImMessagePushService imMessagePushService;
    private final ConversationDraftService conversationDraftService;
    private final JwtUtils jwtUtils;

    @GetMapping("/sessions")
    public Result<List<ConversationVO>> listSessions(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(chatService.listConversations(userId));
    }

    @PostMapping("/private/{friendId}")
    public Result<ConversationVO> openPrivateChat(
            @PathVariable String friendId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(chatService.getOrCreatePrivateConversation(userId, parseId(friendId)));
    }

    @GetMapping("/sessions/{conversationId}/messages")
    public Result<List<MessageVO>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long beforeId = before != null && !before.isBlank() ? parseId(before) : null;
        return Result.success(chatService.listMessages(userId, parseId(conversationId), beforeId, limit));
    }

    @PostMapping("/sessions/{conversationId}/upload")
    @RateLimit(scope = "chat:upload", value = 20, window = 60)
    public Result<ChatFileUploadVO> uploadFile(
            @PathVariable String conversationId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(chatService.uploadChatFile(userId, parseId(conversationId), file));
    }

    /** 鉴权中转下载聊天附件（会话成员） */
    @GetMapping("/messages/{messageId}/file")
    public ResponseEntity<InputStreamResource> downloadMessageFile(
            @PathVariable String messageId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long id = parseId(messageId);
        String name = chatService.getMessageFileName(userId, id);
        var object = chatService.openMessageFile(userId, id);
        return MediaStreamResponses.download(object, name);
    }

    @GetMapping("/search")
    public Result<List<ChatSearchHitVO>> search(
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long convId = conversationId != null && !conversationId.isBlank() ? parseId(conversationId) : null;
        return Result.success(chatService.searchMessages(userId, q, type, convId, limit));
    }

    @PostMapping("/sessions/{conversationId}/messages/{messageId}/recall")
    @RateLimit(scope = "chat:recall", value = 30, window = 60)
    public Result<MessageVO> recallMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        MessageVO vo = chatService.recallMessage(userId, parseId(conversationId), parseId(messageId));
        imMessagePushService.pushRecallToConversationMembers(vo);
        return Result.success(vo);
    }

    @PostMapping("/sessions/{conversationId}/messages/{messageId}/edit")
    @RateLimit(scope = "chat:edit", value = 20, window = 60)
    public Result<MessageVO> editMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String newContent = body.get("content");
        if (newContent == null || newContent.isBlank()) {
            throw new com.linkx.server.exception.CustomException(400, "编辑内容不能为空");
        }
        MessageVO vo = chatService.editMessage(userId, parseId(conversationId), parseId(messageId), newContent);
        return Result.success(vo);
    }

    @PostMapping("/sessions/{conversationId}/messages/{messageId}/forward")
    @RateLimit(scope = "chat:forward", value = 30, window = 60)
    public Result<MessageVO> forwardMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Long> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long targetConversationId = body.get("targetConversationId");
        if (targetConversationId == null) {
            throw new com.linkx.server.exception.CustomException(400, "目标会话 ID 不能为空");
        }
        MessageVO vo = chatService.forwardMessage(userId, parseId(conversationId), parseId(messageId), targetConversationId);
        return Result.success(vo);
    }

    @PostMapping("/sessions/{conversationId}/messages/{messageId}/quote")
    @RateLimit(scope = "chat:quote", value = 30, window = 60)
    public Result<MessageVO> quoteMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @org.springframework.web.bind.annotation.RequestBody SendMessageDTO body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        MessageVO vo = chatService.quoteMessage(userId, parseId(conversationId), parseId(messageId), body);
        return Result.success(vo);
    }

    @PostMapping("/sessions/{conversationId}/read")
    public Result<Long> markAsRead(
            @PathVariable String conversationId,
            @RequestParam("lastMessageId") String lastMessageId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long unreadCount = chatService.markAsRead(userId, parseId(conversationId), parseId(lastMessageId));
        // 广播已读回执给会话其他成员
        imMessagePushService.pushReadReceipt(parseId(conversationId), userId, parseId(lastMessageId));
        return Result.success(unreadCount);
    }

    /**
     * 获取消息已读人数（群聊场景）。
     * 返回 { readCount, totalMembers } 结构。
     */
    @GetMapping("/sessions/{conversationId}/messages/{messageId}/read-count")
    public Result<java.util.Map<String, Object>> getMessageReadCount(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        chatService.assertConversationMember(userId, parseId(conversationId));
        long totalMembers = chatService.getMemberCount(parseId(conversationId));
        long readCount = imMessagePushService.getMessageReadCount(
                parseId(conversationId), parseId(messageId), (int) totalMembers);
        return Result.success(java.util.Map.of(
                "readCount", readCount,
                "totalMembers", totalMembers
        ));
    }

    @GetMapping("/sessions/{conversationId}/unread")
    public Result<Long> getUnreadCount(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        Long unreadCount = chatService.getUnreadCount(userId, parseId(conversationId));
        return Result.success(unreadCount);
    }

    @GetMapping("/unread-total")
    public Result<Long> getTotalUnreadCount(HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        return Result.success(chatService.getTotalUnreadCount(userId));
    }

    @PostMapping("/sessions/{conversationId}/pin")
    public Result<Void> togglePin(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        chatService.togglePinConversation(userId, parseId(conversationId));
        return Result.success();
    }

    @PostMapping("/sessions/{conversationId}/mute")
    public Result<Void> toggleMute(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        chatService.toggleMuteConversation(userId, parseId(conversationId));
        return Result.success();
    }

    // ==================== 会话草稿 ====================

    @PostMapping("/sessions/{conversationId}/draft")
    public Result<Void> saveDraft(
            @PathVariable String conversationId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String content = body.getOrDefault("content", "");
        conversationDraftService.saveDraft(userId, parseId(conversationId), content);
        return Result.success();
    }

    @GetMapping("/sessions/{conversationId}/draft")
    public Result<String> getDraft(
            @PathVariable String conversationId,
            HttpServletRequest request) {
        Long userId = AuthUtils.requireUserId(request, jwtUtils);
        String draft = conversationDraftService.getDraft(userId, parseId(conversationId));
        return Result.success(draft);
    }

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
