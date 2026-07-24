package com.linkx.server.entity;

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
 * 消息风暴事件落库，便于审计与报表（Redis 计数之外的持久化）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_message_storm_event")
public class ImMessageStormEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_USER_RATE = "user_rate";
    public static final String TYPE_GROUP_RATE = "group_rate";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;
    private Long conversationId;
    /** user_rate / group_rate */
    private String eventType;
    private Integer messageCount;
    private Integer windowSeconds;
    private Integer memberCount;
    private Date createTime;
}
