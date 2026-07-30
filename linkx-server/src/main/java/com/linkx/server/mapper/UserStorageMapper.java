package com.linkx.server.mapper;

import com.linkx.server.entity.UserStorage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserStorageMapper extends BaseMapper<UserStorage> {

    /** 原子扣减用量并递增版本号，返回 1 表示成功（CAS）；0 表示并发冲突或结果将为负 */
    @Update("UPDATE user_storage SET used_bytes = used_bytes + #{delta}, file_count = file_count + #{fileCountDelta}, version = version + 1 " +
            "WHERE user_id = #{userId} AND version = #{expectedVersion} " +
            "AND used_bytes + #{delta} >= 0 AND file_count + #{fileCountDelta} >= 0")
    int casUpdateUsedBytes(@Param("userId") Long userId,
                           @Param("delta") long delta,
                           @Param("fileCountDelta") int fileCountDelta,
                           @Param("expectedVersion") int expectedVersion);

    /** 原子扩容配额并递增版本号（自动扩容场景） */
    @Update("UPDATE user_storage SET quota_bytes = #{newQuota}, version = version + 1 WHERE user_id = #{userId} AND version = #{expectedVersion}")
    int casExpandQuota(@Param("userId") Long userId,
                       @Param("newQuota") long newQuota,
                       @Param("expectedVersion") int expectedVersion);
}
