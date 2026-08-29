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
@Table("short_video_post")
public class ShortVideoPost implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String description;

    private Byte descriptionEncVersion;

    private String searchText;

    private String videoKey;

    /** 上传时对象存储后端：minio | oss | cos | r2 */
    private String storageProvider;

    private String coverKey;

    private Integer durationMs;

    private Integer visibility;

    private Long playCount;

    private Long shareCount;

    /** skipped | pending | processing | completed | failed */
    private String transcodeStatus;

    private String transcodedVideoKey;

    /** 转码失败时的简要原因（管理端展示） */
    private String transcodeError;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
