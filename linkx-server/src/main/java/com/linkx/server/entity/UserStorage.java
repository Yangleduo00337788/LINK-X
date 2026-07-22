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
@Table("user_storage")
public class UserStorage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认配额 20 GiB */
    public static final long DEFAULT_QUOTA_BYTES = 20L * 1024 * 1024 * 1024;
    /** 超限自动扩容步长 10 GiB */
    public static final long EXPAND_STEP_BYTES = 10L * 1024 * 1024 * 1024;
    /** 最大配额 60 GiB */
    public static final long MAX_QUOTA_BYTES = 60L * 1024 * 1024 * 1024;

    @Id
    private Long userId;

    private Long quotaBytes;

    private Long usedBytes;

    private Integer fileCount;

    private Integer version;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
