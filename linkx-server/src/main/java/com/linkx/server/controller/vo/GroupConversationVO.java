package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群聊会话 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupConversationVO {

    private Long id;

    private Integer type;

    private String name;

    private String avatar;

    private String announcement;

    private Long ownerId;

    private String ownerNickname;

    private Integer memberCount;

    private String lastMessage;

    private Long lastMessageTime;
}
