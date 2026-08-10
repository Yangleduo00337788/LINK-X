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
@Schema(description = "风险事件摘要")
public class AdminRiskEventBriefVO {

    private Long id;
    private String eventType;
    private String title;
    private String riskLevel;
    private String status;
    private Date createTime;
}
