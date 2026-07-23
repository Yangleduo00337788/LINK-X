package com.linkx.server.service;

/**
 * 会话草稿（按用户 + 会话维度，Redis 存储）。
 */
public interface ConversationDraftService {

    void saveDraft(Long userId, Long conversationId, String content);

    String getDraft(Long userId, Long conversationId);

    void clearDraft(Long userId, Long conversationId);
}
