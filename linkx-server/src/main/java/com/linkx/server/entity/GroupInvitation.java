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

/**
 * 群邀请实体。
 * <p>
 * 一条邀请表示：{@code inviterUserId} 邀请 {@code inviteeUserId} 加入会话 {@code conversationId}。
 * 状态：待处理 / 已同意 / 已拒绝 / 已过期。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("group_invitation")
public class GroupInvitation implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_EXPIRED = 3;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long conversationId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String message;

    private Integer status;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
