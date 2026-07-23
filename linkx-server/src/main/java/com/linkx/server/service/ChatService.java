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

    /** 获取所有会话的未读总数（用于角标）。 */
    long getTotalUnreadCount(Long userId);

    /** 编辑已发送的消息（仅文本消息，仅发送者，24 小时内）。 */
    MessageVO editMessage(Long userId, Long conversationId, Long messageId, String newContent);

    /** 转发消息到另一个会话。 */
    MessageVO forwardMessage(Long userId, Long sourceConversationId, Long sourceMessageId, Long targetConversationId);

    /** 引用回复消息。 */
    MessageVO quoteMessage(Long userId, Long conversationId, Long quoteMessageId, SendMessageDTO dto);

    /** 置顶/取消置顶会话。 */
    void togglePinConversation(Long userId, Long conversationId);

    /** 免打扰/取消免打扰会话。 */
    void toggleMuteConversation(Long userId, Long conversationId);

    /** 获取会话成员总数（含已删除成员需传 includeDeleted=true）。 */
    long getMemberCount(Long conversationId);

    // ==================== 分片上传（断点续传） ====================

    /** 初始化分片上传 */
    String initiateMultipartUpload(Long userId, Long conversationId, String objectName, String contentType);

    /** 上传分片 */
    String uploadPart(Long userId, Long conversationId, String objectName, String uploadId, int partNumber, MultipartFile file);

    /** 完成分片上传 */
    ChatFileUploadVO completeMultipartUpload(Long userId, Long conversationId, String objectName, String uploadId, List<FileStorageService.PartETag> parts);

    /** 取消分片上传 */
    void abortMultipartUpload(Long userId, Long conversationId, String objectName, String uploadId);

    /** 根据文件哈希查找已上传文件 */
    String findFileByHash(Long userId, String contentHash);
}
