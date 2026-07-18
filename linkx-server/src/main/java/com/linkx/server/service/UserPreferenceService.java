package com.linkx.server.service;

import com.linkx.server.entity.UserPreference;

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
}