package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 群邀请响应 VO（GET /group/invitations）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 群名称（冗余） */
    private String groupName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long inviterUserId;

    /** 邀请人昵称（冗余） */
    private String inviterNickname;

    /** 邀请人头像（冗余） */
    private String inviterAvatar;

    /** 邀请留言 */
    private String message;

    /** 状态：0 待处理 / 1 已同意 / 2 已拒绝 / 3 已过期 */
    private Integer status;

    /** ISO 字符串或毫秒时间戳 */
    private Long createTime;
}
