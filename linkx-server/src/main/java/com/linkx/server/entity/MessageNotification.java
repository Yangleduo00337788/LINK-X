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
 * 消息通知实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("message_notification")
public class MessageNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String type;

    private Long relatedId;

    private String content;

    private Integer readStatus;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
