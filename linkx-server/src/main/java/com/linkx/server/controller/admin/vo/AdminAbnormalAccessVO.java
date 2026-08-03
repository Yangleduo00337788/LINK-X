package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "异常访问记录")
public class AdminAbnormalAccessVO {

    @Schema(description = "来源：login_fail / rate_limit / risk_event")
    private String source;

    @Schema(description = "来源侧主键或 Redis key")
    private String sourceId;

    @Schema(description = "分类标签")
    private String category;

    private String title;
    private String detail;
    private String ip;
    private String region;
    private String username;
    private String identity;
    private Long hitCount;
    private Long ttlSeconds;
    private String riskLevel;
    private String status;
    private Date occurredAt;
}
