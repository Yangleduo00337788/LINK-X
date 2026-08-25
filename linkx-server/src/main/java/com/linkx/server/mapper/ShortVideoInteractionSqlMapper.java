package com.linkx.server.mapper;

import com.linkx.server.mapper.row.ShortVideoCommentCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 短视频点赞/收藏聚合查询（避免列表接口拉全量互动行）。
 */
@Mapper
public interface ShortVideoInteractionSqlMapper {

    @Select("""
            <script>
            SELECT post_id AS postId, COUNT(*) AS count
            FROM short_video_like
            WHERE deleted = 0 AND post_id IN
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            GROUP BY post_id
            </script>
            """)
    List<ShortVideoCommentCountRow> countLikesByPostIds(@Param("postIds") List<Long> postIds);

    @Select("""
            <script>
            SELECT post_id AS postId, COUNT(*) AS count
            FROM short_video_favorite
            WHERE deleted = 0 AND post_id IN
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            GROUP BY post_id
            </script>
            """)
    List<ShortVideoCommentCountRow> countFavoritesByPostIds(@Param("postIds") List<Long> postIds);

    @Select("""
            <script>
            SELECT post_id
            FROM short_video_like
            WHERE deleted = 0 AND user_id = #{viewerId} AND post_id IN
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<Long> likedPostIdsByViewer(@Param("viewerId") Long viewerId, @Param("postIds") List<Long> postIds);

    @Select("""
            <script>
            SELECT post_id
            FROM short_video_favorite
            WHERE deleted = 0 AND user_id = #{viewerId} AND post_id IN
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<Long> favoritedPostIdsByViewer(@Param("viewerId") Long viewerId, @Param("postIds") List<Long> postIds);
}
