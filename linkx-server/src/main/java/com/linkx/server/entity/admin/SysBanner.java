package com.linkx.server.entity.admin;

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
@Table("sys_banner")
public class SysBanner implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_UNPUBLISHED = "unpublished";

    public static final String POSITION_HOME = "home";
    public static final String POSITION_LOGIN = "login";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String title;
    private String imageUrl;
    private String linkUrl;
    /** home / login */
    private String position;
    private Integer sortOrder;
    private String status;
    private Date startAt;
    private Date endAt;
    private Date publishedAt;
    private Long publishedBy;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
