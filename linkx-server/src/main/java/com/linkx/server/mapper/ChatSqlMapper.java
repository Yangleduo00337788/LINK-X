package com.linkx.server.mapper;


import com.linkx.server.mapper.row.ConversationUnreadRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * IM 会话相关手写 SQL（批量未读统计等）。
 */
@Mapper
public interface ChatSqlMapper {

    @Select("""
            SELECT m.conversation_id AS conversationId, COUNT(*) AS unreadCount
            FROM im_message m
            INNER JOIN im_conversation_member cm
                ON cm.conversation_id = m.conversation_id
               AND cm.user_id = #{userId}
               AND cm.deleted = 0
            WHERE m.deleted = 0
              AND m.sender_id <> #{userId}
              AND m.type NOT IN ('recall', 'system')
              AND m.id > COALESCE(cm.last_read_message_id, 0)
            GROUP BY m.conversation_id
            """)
    List<ConversationUnreadRow> batchUnreadCounts(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM im_message m
            INNER JOIN im_conversation_member cm
                ON cm.conversation_id = m.conversation_id
               AND cm.user_id = #{userId}
               AND cm.deleted = 0
            WHERE m.deleted = 0
              AND m.sender_id <> #{userId}
              AND m.type NOT IN ('recall', 'system')
              AND m.id > COALESCE(cm.last_read_message_id, 0)
            """)
    long totalUnreadCount(@Param("userId") Long userId);
}
