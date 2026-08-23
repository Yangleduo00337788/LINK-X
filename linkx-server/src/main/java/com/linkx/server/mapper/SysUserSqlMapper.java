package com.linkx.server.mapper;


import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.row.AdminOverviewCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface SysUserSqlMapper {

    @Select("""
            SELECT id, username, nickname, avatar, email, status, dept_id AS deptId,
                   create_time AS createTime, update_time AS updateTime, deleted
            FROM sys_user
            WHERE status = 1 AND deleted = 0
              AND nickname IS NOT NULL
              AND nickname <> ''
              AND MATCH(nickname) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
            LIMIT #{limit}
            """)
    List<SysUser> searchByNicknameFulltext(@Param("keyword") String keyword, @Param("limit") int limit);
}
