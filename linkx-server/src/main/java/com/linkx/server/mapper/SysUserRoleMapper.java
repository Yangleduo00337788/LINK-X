package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysUserRole;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联表数据访问接口。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
