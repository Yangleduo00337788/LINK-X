package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "操作日志")
public class AdminOperationLogVO {

    private Long id;
    private String operationType;
    private String description;
    private Long userId;
    private String username;
    private Long targetUserId;
    private String targetUsername;
    private String targetResourceId;
    private String targetResourceType;
    private String ip;
    private String userAgent;
    private String status;
    private String failureReason;
    private String extraData;
    private Date createTime;
}
