package com.linkx.server.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_conversation")
public class ImConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_GROUP = 2;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Integer type;

    private String privateKey;

    /**
     * 群名称（群聊用）
     */
    private String name;

    /**
     * 群头像 URL
     */
    private String avatar;

    /**
     * 群公告
     */
    private String announcement;

    /**
     * 群主 ID（群聊用）
     */
    private Long ownerId;

    /**
     * 全体禁言：开启后仅群主与管理员可发言
     */
    private Integer muteAll;

    /**
     * 定时全体禁言开始时间（到达后自动开启）
     */
    private Date muteAllStart;

    /**
     * 定时全体禁言结束时间（到达后自动关闭）
     */
    private Date muteAllEnd;

    /**
     * 入群审批：0 不需要，1 需要管理员审批
     */
    @Builder.Default
    private Integer joinApproval = 0;

    /**
     * 邀请策略：ownerApprove = 需群主审批，anyMember = 任何人可邀请
     */
    @Builder.Default
    private String invitePolicy = "anyMember";

    private String lastMessageContent;

    private Date lastMessageTime;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
