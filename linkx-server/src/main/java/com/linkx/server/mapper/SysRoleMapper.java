package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysRole;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统角色表数据访问接口。
 * <p>
 * 继承 BaseMapper 后自动拥有 CRUD 与链式查询能力，
 * RBAC 业务通过 RbacService 组合使用。
 * </p>
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
