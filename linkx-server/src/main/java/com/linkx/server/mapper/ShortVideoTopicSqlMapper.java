package com.linkx.server.mapper;


/**
 * 作者：yangleduo
 */
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

@Mapper
public interface ShortVideoTopicSqlMapper {

    @Select("""
            SELECT MAX(p.create_time)
            FROM short_video_post_topic pt
            INNER JOIN short_video_post p ON p.id = pt.post_id AND p.deleted = 0
            WHERE pt.topic_id = #{topicId}
            """)
    Date findLastPostAt(@Param("topicId") Long topicId);
}
