// MyBatis Mapper 接口包
package com.linkx.server.mapper;

// 用户实体类
import com.linkx.server.entity.SysUser;
// MyBatis-Flex 基础 Mapper，提供 CRUD 及链式查询
import com.mybatisflex.core.BaseMapper;
// MyBatis 注解：标记为 Mapper 接口
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户表数据访问接口。
 * <p>
 * 继承 BaseMapper 后自动拥有 insert/update/delete/select 等方法，
 * Service 层通过 MyBatis-Flex 的 queryChain() 进行条件查询。
 * </p>
 */
@Mapper // 注册为 MyBatis Mapper，由 @MapperScan 扫描
public interface SysUserMapper extends BaseMapper<SysUser> {
    // 暂无自定义 SQL 方法，使用 BaseMapper 与 MyBatis-Flex 链式 API 即可
}
