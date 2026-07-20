package com.linkx.server.service;

import com.linkx.server.entity.UserPreference;

import java.util.Collection;
import java.util.Map;

/**
 * 用户偏好设置 Service
 * 提供 per-user 一行的偏好读取与局部更新。
 */
public interface UserPreferenceService {

    /**
     * 获取指定用户的偏好设置；不存在时返回默认值（不创建行）。
     */
    UserPreference getOrDefault(Long userId);

    /**
     * 局部更新偏好设置（PUT 语义，null 字段保持不变）。
     * 当用户尚不存在偏好行时插入新行，否则按非空字段覆盖。
     */
    UserPreference upsert(Long userId, UserPreference patch);

    /** 加好友是否需要对方验证（默认 true） */
    boolean requiresFriendVerify(Long userId);

    /** 是否允许陌生人发起私聊（默认 false） */
    boolean allowsStrangerChat(Long userId);

    /** 是否对外展示在线状态（默认 true） */
    boolean showsOnlineStatus(Long userId);

    /**
     * 批量查询「是否对外展示在线状态」。
     * 未落库的用户按默认 true。
     */
    Map<Long, Boolean> batchShowsOnlineStatus(Collection<Long> userIds);
}
