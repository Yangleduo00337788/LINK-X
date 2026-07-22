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
@Table("cloud_activity")
public class CloudActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TARGET_FILE = "file";
    public static final String TARGET_FOLDER = "folder";
    public static final String TARGET_STORAGE = "storage";

    public static final String ACTION_UPLOAD = "upload";
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_RENAME = "rename";
    public static final String ACTION_MOVE = "move";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_SHARE = "share";
    public static final String ACTION_TAG = "tag";
    public static final String ACTION_DOWNLOAD = "download";
    public static final String ACTION_EXPAND = "expand";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    private String targetName;

    private String action;

    private String detail;

    @Column(onInsertValue = "NOW()")
    private Date createTime;
}
