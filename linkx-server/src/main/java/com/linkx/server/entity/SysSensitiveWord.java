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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_sensitive_word")
public class SysSensitiveWord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ACTION_FILTER = "filter";
    public static final String ACTION_BLOCK = "block";
    public static final String ACTION_ALERT = "alert";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 敏感词 */
    private String word;

    /** 分类: general/politics/violence/ad */
    private String category;

    /** 处理方式: filter(替换) / block(拦截) / alert(警告) */
    private String action;

    /** 替换文本(仅filter时生效) */
    private String replacement;

    /** 是否启用 */
    private Boolean enabled;

    private Date createTime;
    private Date updateTime;
}
