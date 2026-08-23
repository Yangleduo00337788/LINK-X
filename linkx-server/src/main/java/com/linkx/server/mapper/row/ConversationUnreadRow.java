package com.linkx.server.mapper.row;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationUnreadRow {

    private Long conversationId;
    private Long unreadCount;
}
