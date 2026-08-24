package com.linkx.server.mapper;


import com.linkx.server.mapper.row.ShortVideoCommentCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShortVideoCommentSqlMapper {

    @Select("""
            <script>
            SELECT post_id AS postId, COUNT(*) AS count
            FROM short_video_comment
            WHERE deleted = 0 AND post_id IN
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            GROUP BY post_id
            </script>
            """)
    List<ShortVideoCommentCountRow> countByPostIds(@Param("postIds") List<Long> postIds);
}
