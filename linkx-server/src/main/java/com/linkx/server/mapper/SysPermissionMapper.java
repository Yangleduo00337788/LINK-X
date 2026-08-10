package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysPermission;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统权限表数据访问接口。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
