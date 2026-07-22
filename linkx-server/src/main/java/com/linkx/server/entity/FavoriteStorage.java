package com.linkx.server.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
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
@Table("favorite_storage")
public class FavoriteStorage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认配额 30 GiB */
    public static final long DEFAULT_QUOTA_BYTES = 30L * 1024 * 1024 * 1024;

    @Id
    private Long userId;

    private Long quotaBytes;

    private Long usedBytes;

    private Integer itemCount;

    private Integer version;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
