package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.ImMessage;

/**
 * 群聊 AI 自动化：主动发言与手动智能总结。
 */
public interface GroupAiAutomationService {

    /**
     * 群成员发送消息后触发（事务提交后异步执行）。
     */
    void onGroupUserMessage(Long conversationId, ImMessage message);

    /**
     * 成员手动触发智能总结，返回发送到群内的总结消息。
     */
    com.linkx.server.controller.vo.MessageVO triggerSmartSummary(Long userId, Long conversationId);
}
