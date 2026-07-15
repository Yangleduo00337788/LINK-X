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

    private String lastMessageContent;

    private Date lastMessageTime;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
