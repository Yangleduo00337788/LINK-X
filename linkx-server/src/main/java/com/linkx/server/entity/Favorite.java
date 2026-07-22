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
 * 用户收藏（与笔记独立）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("favorite")
public class Favorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** note / image / link / file / message */
    private String type;

    private String sourceType;

    private String sourceId;

    /** JSON 字符串数组，如 ["工作","学习"] */
    private String tags;

    /** 文件/图片大小（字节） */
    private Long fileSize;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
