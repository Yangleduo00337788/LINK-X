package com.linkx.server.mapper;

import com.linkx.server.entity.FavoriteStorage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FavoriteStorageMapper extends BaseMapper<FavoriteStorage> {

    /** 原子更新用量/条目数并递增版本号；返回 1 表示 CAS 成功 */
    @Update("UPDATE favorite_storage SET used_bytes = used_bytes + #{delta}, item_count = item_count + #{itemCountDelta}, version = version + 1 WHERE user_id = #{userId} AND version = #{expectedVersion}")
    int casUpdateUsedBytes(@Param("userId") Long userId,
                           @Param("delta") long delta,
                           @Param("itemCountDelta") int itemCountDelta,
                           @Param("expectedVersion") int expectedVersion);

    /** 原子扩容配额并递增版本号 */
    @Update("UPDATE favorite_storage SET quota_bytes = #{newQuota}, version = version + 1 WHERE user_id = #{userId} AND version = #{expectedVersion}")
    int casExpandQuota(@Param("userId") Long userId,
                       @Param("newQuota") long newQuota,
                       @Param("expectedVersion") int expectedVersion);
}
