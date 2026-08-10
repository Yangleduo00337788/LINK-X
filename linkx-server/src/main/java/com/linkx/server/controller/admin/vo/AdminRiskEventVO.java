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
@Schema(description = "风险事件")
public class AdminRiskEventVO {

    private Long id;
    private String eventType;
    private String title;
    private String detail;
    private String riskLevel;
    private String status;
    private Long userId;
    private String username;
    private String targetResourceId;
    private String targetResourceType;
    private String ip;
    @Schema(description = "IP 归属地")
    private String region;
    private String extraData;
    private Long auditLogId;
    private String resolution;
    private Long handledBy;
    private Date handledAt;
    private Date createTime;
}
