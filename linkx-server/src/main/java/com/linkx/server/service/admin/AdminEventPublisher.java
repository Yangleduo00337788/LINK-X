package com.linkx.server.service.admin;

/**
 * 管理端实时事件发布（Redis Pub/Sub → SSE 扇出）。
 */
public interface AdminEventPublisher {

    String CHANNEL = "linkx:admin:events";

    void publish(String type, Long relatedId);

    void publish(String type, Long relatedId, String extraJson);
}
