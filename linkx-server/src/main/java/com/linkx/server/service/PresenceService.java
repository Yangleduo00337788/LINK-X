package com.linkx.server.service;

/**
 * 集群级用户在线状态（Redis presence）。
 * <p>
 * 本地 {@code ImChannelManager} 只负责本机 Channel 扇出；跨实例在线判定以本服务为准。
 * </p>
 */
public interface PresenceService {

    /**
     * 登记一条本机连接；若用户从离线变为在线则广播 online 事件。
     *
     * @param connId 连接唯一 ID（同一 deviceId 多标签页需区分）
     */
    void markOnline(Long userId, String deviceId, String connId);

    /**
     * 移除一条本机连接；若用户全局无剩余连接则广播 offline 事件。
     */
    void markOffline(Long userId, String deviceId, String connId);

    /**
     * 心跳续期 presence TTL。
     */
    void touch(Long userId);

    /**
     * 集群视角是否在线（Redis 连接集合非空）。
     */
    boolean isOnline(Long userId);
}
