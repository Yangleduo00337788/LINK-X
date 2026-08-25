package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
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
@Table("short_video_topic")
public class ShortVideoTopic implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String name;

    private Integer postCount;

    private Integer pinned;

    private Integer pinOrder;

    private Integer status;

    private String displayName;

    private Double hotScore;

    private Date lastPostAt;

    @Column(onInsertValue = "NOW()")
    private Date createTime;
}
