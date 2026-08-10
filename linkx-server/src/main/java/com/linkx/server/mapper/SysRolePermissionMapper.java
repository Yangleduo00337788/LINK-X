package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysRolePermission;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联表数据访问接口。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
