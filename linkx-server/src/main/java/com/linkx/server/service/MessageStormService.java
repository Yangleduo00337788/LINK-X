package com.linkx.server.service;

/**
 * 消息风暴检测：Redis 限流 + DB 事件落库。
 */
public interface MessageStormService {

    /**
     * 用户级发送频率检测（WS 入口）。
     *
     * @return true 表示已超限，应拒绝发送
     */
    boolean checkAndRecordUserStorm(Long userId);

    /**
     * 超大群发言频率限制；超限抛 429。
     */
    void checkAndRecordGroupStorm(Long userId, Long conversationId, int memberCount);

    /** 查询用户近期风暴事件数（测试/运维） */
    long countRecentEvents(Long userId);
}
