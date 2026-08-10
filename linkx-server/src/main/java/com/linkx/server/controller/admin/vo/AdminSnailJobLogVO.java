package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminSnailJobLogVO {
    private Long id;
    private Long taskBatchId;
    private Long taskId;
    private String message;
    private Integer logNum;
    private LocalDateTime createDt;
}
