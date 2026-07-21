package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** 成员头像预览（拼图用，最多 9 个） */
    private List<GroupMemberAvatarVO> memberAvatars;

    private String announcement;

    private Long ownerId;

    private String ownerNickname;

    private Integer memberCount;

    private String lastMessage;

    private Long lastMessageTime;

    /** 当前用户对本群的备注 */
    private String myRemark;
}
