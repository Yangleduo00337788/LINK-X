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

    /** 是否全体禁言（含定时生效中） */
    private Boolean muteAll;

    /** 定时全体禁言开始（毫秒） */
    private Long muteAllStart;

    /** 定时全体禁言结束（毫秒） */
    private Long muteAllEnd;

    /** 当前用户是否被个人禁言（生效中） */
    private Boolean meMuted;

    /** 当前用户个人禁言截止（毫秒，可空） */
    private Long meMuteUntil;

    /** 入群是否需要审批 */
    private Boolean joinApproval;

    /** 邀请策略：anyMember / ownerApprove */
    private String invitePolicy;
}
