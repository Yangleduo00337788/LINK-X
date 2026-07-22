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
@Table("cloud_share")
public class CloudShare implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_FILE = "file";
    public static final String TYPE_FOLDER = "folder";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String shareType;

    private Long targetId;

    private String token;

    private String passwordHash;

    private Date expireAt;

    private Integer maxDownloads;

    private Integer downloadCount;

    /** 1 有效 0 关闭 */
    private Integer status;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
