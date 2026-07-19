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
@Table("moments_comment")
public class MomentsComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long postId;

    private Long userId;

    private String content;

    private Long parentId;

    /**
     * 被 @ 的用户 ID 列表，JSON 数组字符串：[12, 34]
     */
    private String mentions;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
