package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.CloudShare;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CloudShareMapper extends BaseMapper<CloudShare> {

    /**
     * 原子递增分享下载次数，仅当分享有效且未超限时才更新成功（返回 1）。
     * max_downloads 为 NULL 视为不限次数；关闭或超限时返回 0。
     * 通过受影响行数判断，避免 check-then-set TOCTOU 竞态。
     */
    @Update("UPDATE cloud_share SET download_count = download_count + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 1 " +
            "AND (max_downloads IS NULL OR download_count < max_downloads)")
    int incrementDownloadCount(@Param("id") Long id);
}
