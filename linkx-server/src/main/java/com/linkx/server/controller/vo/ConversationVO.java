package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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
     * 单聊对方是否在线（受对方「在线状态可见」偏好约束；群聊为空）
     */
    private Boolean peerOnline;

    /**
     * 群名称（群聊用）
     */
    private String name;

    /**
     * 群头像（群聊用，已签发；有自定义群头像时优先）
     */
    private String avatar;

    /**
     * 群成员头像预览（最多 9 个，用于默认拼图头像）
     */
    private List<GroupMemberAvatarVO> memberAvatars;

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
