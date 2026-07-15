package com.linkx.server.controller;

import com.linkx.server.common.AuthUtils;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.RateLimit;
import com.linkx.server.common.Result;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new com.linkx.server.exception.CustomException(400, "无效的 ID");
        }
    }
}
