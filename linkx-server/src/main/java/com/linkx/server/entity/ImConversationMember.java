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
     * 是否被禁言（1=是）
     */
    private Integer muted;

    /**
     * 禁言截止时间；为空表示需手动解除
     */
    private Date muteUntil;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
