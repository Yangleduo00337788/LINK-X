package com.linkx.server.service;

/**
 * 集群级用户在线状态（Redis presence）。
 * <p>
 * 本地 {@code ImChannelManager} 只负责本机 Channel 扇出；跨实例在线判定以本服务为准。
 * </p>
 */
public interface PresenceService {

    /** 当前 JVM 实例 ID（用于跨实例推送去重、宕机清扫）。 */
    String getInstanceId();

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
     * 心跳续期 presence TTL，并刷新本实例存活心跳。
     */
    void touch(Long userId);

    /**
     * 集群视角是否在线（Redis 连接集合非空）。
     */
    boolean isOnline(Long userId);

    /**
     * 强制对外广播在线可见性（不修改连接集合）。
     * 用于「在线状态可见」开关：关 → offline，开且仍在线 → online。
     */
    void broadcastPresence(Long userId, boolean online);

    /**
     * 优雅停机：清理本实例全部 presence 成员并在必要时广播 offline。
     */
    void clearLocalPresenceOnShutdown();

    /**
     * 刷新本实例心跳；供定时任务调用。
     */
    void refreshInstanceHeartbeat();

    /**
     * 清扫已无心跳的其它实例，移除其僵尸连接并广播 offline。
     */
    void sweepDeadInstances();
}
