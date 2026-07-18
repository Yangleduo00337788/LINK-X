package com.linkx.server.mapper;

import com.linkx.server.entity.UserPreference;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户偏好设置 Mapper
 * 主键为 userId（一行一用户）。
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}