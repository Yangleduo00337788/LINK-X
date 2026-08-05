package com.linkx.server.service.admin;

/**
 * 管理端实时事件发布（Redis Pub/Sub → SSE 扇出）。
 */
public interface AdminEventPublisher {

    String CHANNEL = "linkx:admin:events";

    void publish(String type, Long relatedId);

    void publish(String type, Long relatedId, String extraJson);

    /**
     * 定向推送给指定管理员；targetUserIds 为空时等同全局广播。
     */
    void publishToUsers(String type, Long relatedId, java.util.Collection<Long> targetUserIds);

    void publishToUsers(String type, Long relatedId, java.util.Collection<Long> targetUserIds, String extraJson);
}
