package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer type;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long peerUserId;

    private String peerUsername;
    private String peerNickname;
    private String peerAvatar;
    private String peerRemark;

    /**
     * 群名称（群聊用）
     */
    private String name;

    /**
     * 群公告（群聊用）
     */
    private String announcement;

    /**
     * 群主 ID（群聊用）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private String lastMessage;
    private Long lastMessageTime;
}
