// MyBatis Mapper 接口包
package com.linkx.server.mapper;

// 好友关系实体
import com.linkx.server.entity.SysUserRelation;
// MyBatis-Flex 通用 Mapper 父接口
import com.mybatisflex.core.BaseMapper;
// MyBatis Mapper 标记
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户好友关系表数据访问接口。
 * <p>
 * 当前为预留接口，好友相关 Service 尚未实现。
 * </p>
 */
@Mapper // 由 Spring 扫描并绑定 sys_user_relation 表
public interface SysUserRelationMapper extends BaseMapper<SysUserRelation> {
    // 暂无自定义方法，后续可在此声明联表查询好友列表等 SQL
}
