package com.linkx.server.service;

import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatFileUploadVO;
import com.linkx.server.controller.vo.ChatSearchHitVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    List<ConversationVO> listConversations(Long userId);

    ConversationVO getOrCreatePrivateConversation(Long userId, Long friendId);

    List<MessageVO> listMessages(Long userId, Long conversationId, Long beforeMessageId, int limit);

    MessageVO sendMessage(Long userId, SendMessageDTO dto);

    /**
     * 撤回消息（仅发送者，默认 2 分钟内）。消息原地改为 type=recall 并清空载荷。
     */
    MessageVO recallMessage(Long userId, Long conversationId, Long messageId);

    /**
     * 写入系统提示消息（服务端内部调用，不走客户端上行校验）。
     */
    MessageVO postSystemMessage(Long operatorId, Long conversationId, String content);

    ChatFileUploadVO uploadChatFile(Long userId, Long conversationId, MultipartFile file);

    /**
     * 服务端聊天记录搜索（当前用户所在会话）
     */
    List<ChatSearchHitVO> searchMessages(Long userId, String keyword, String type, Long conversationId, int limit);

    void assertConversationMember(Long userId, Long conversationId);

    /**
     * 鉴权后打开聊天消息附件流（会话成员）。
     */
    FileStorageService.StoredObject openMessageFile(Long userId, Long messageId);

    /** 消息附件展示名（鉴权后）。 */
    String getMessageFileName(Long userId, Long messageId);

    /** 将会话中某条及之前的消息标记为已读，并返回会话最新未读数。 */
    long markAsRead(Long userId, Long conversationId, Long lastReadMessageId);

    /** 获取单个会话的未读数。 */
    long getUnreadCount(Long userId, Long conversationId);
}
