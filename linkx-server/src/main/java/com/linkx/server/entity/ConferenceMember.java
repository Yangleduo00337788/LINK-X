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
@Table("conference_member")
public class ConferenceMember implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_HOST = "host";
    public static final String ROLE_CO_HOST = "co-host";
    public static final String ROLE_MEMBER = "member";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long conferenceId;
    private Long userId;
    private String role;

    @Builder.Default
    private Integer muted = 0;

    @Builder.Default
    private Integer videoOff = 0;

    /** 是否已离开（列名 left_flag，避免 SQL 保留字） */
    @Column("left_flag")
    @Builder.Default
    private Integer leftFlag = 0;

    private Date joinTime;
    private Date leaveTime;

    @Column(onInsertValue = "NOW()")
    private Date createTime;
}
