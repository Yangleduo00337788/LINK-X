package com.linkx.server.service;

import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    List<ConversationVO> listConversations(Long userId);

    ConversationVO getOrCreatePrivateConversation(Long userId, Long friendId);

    List<MessageVO> listMessages(Long userId, Long conversationId, Long beforeMessageId, int limit);

    MessageVO sendMessage(Long userId, SendMessageDTO dto);

    ChatFileUploadVO uploadChatFile(Long userId, Long conversationId, MultipartFile file);

    void assertConversationMember(Long userId, Long conversationId);
}
