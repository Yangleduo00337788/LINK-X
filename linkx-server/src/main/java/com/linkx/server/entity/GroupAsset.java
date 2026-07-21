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
 * 群共享资源：文件 / 相册图片 / 精华
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("group_asset")
public class GroupAsset implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_FILE = "file";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_ESSENCE = "essence";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long conversationId;

    private Long uploaderId;

    /** file / image / essence */
    private String type;

    private String title;

    private String content;

    private String fileName;

    private Long fileSize;

    /** MinIO object key */
    private String fileKey;

    private Long messageId;

    private Integer downloadCount;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
