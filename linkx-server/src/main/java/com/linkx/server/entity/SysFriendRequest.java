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
 * 好友申请实体，对应 sys_friend_request 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_friend_request")
public class SysFriendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已同意 */
    public static final int STATUS_ACCEPTED = 1;
    /** 状态：已拒绝 */
    public static final int STATUS_REJECTED = 2;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private String message;

    private Integer status;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
