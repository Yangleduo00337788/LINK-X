package com.linkx.server.entity.admin;


/**
 * 作者：yangleduo
 */
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
@Table("sys_homepage_section")
public class SysHomepageSection implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_BANNER = "banner";
    public static final String TYPE_RECOMMEND = "recommend";
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_NOTICE = "notice";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String sectionType;
    private String sectionKey;
    private String title;
    private Boolean enabled;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
