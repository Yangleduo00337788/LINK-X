package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminSystemTableStatVO {

    private String tableName;
    private String engine;
    private long rowCount;
    private long dataBytes;
    private long indexBytes;
    private long totalBytes;
    private String tableComment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
