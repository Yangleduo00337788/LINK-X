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
@Table("im_conversation_member")
public class ImConversationMember implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long conversationId;

    private Long userId;

    /**
     * 成员角色：owner（群主）/ admin（管理员）/ member（普通成员）
     */
    private String role;

    /** 用户对本群的备注（仅自己可见，多端同步） */
    private String remark;

    /**
     * 当前成员已读到的最后一条消息 ID，用于未读数计算与多端同步。
     */
    private Long lastReadMessageId;

    /**
     * 是否置顶（1=是）
     */
    @Builder.Default
    private Integer pinned = 0;

    /**
     * 是否标记为重要会话（1=是；列表高亮，独立于置顶）
     */
    @Builder.Default
    private Integer important = 0;

    /**
     * 是否被禁言（1=是）
     */
    @Builder.Default
    private Integer muted = 0;

    /**
     * 禁言截止时间；为空表示需手动解除
     */
    private Date muteUntil;

    /**
     * 免打扰（1=是）
     */
    @Builder.Default
    private Integer mute = 0;

    /**
     * 群公告是否已读
     */
    @Builder.Default
    private Boolean announcementRead = false;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    @Builder.Default
    private Integer deleted = 0;
}
