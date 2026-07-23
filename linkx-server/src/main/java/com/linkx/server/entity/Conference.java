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
@Table("conference")
public class Conference implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_ENDED = 2;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String title;
    private String type;
    private Long creatorId;
    private Long conversationId;
    private Integer status;
    private Integer maxParticipants;
    private Date startTime;
    private Date endTime;
    private String password;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
