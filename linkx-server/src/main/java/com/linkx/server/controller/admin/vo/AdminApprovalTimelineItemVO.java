package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "审批时间线项")
public class AdminApprovalTimelineItemVO {

    private String id;
    private Integer stepIndex;
    private String stepName;
    private String nodeType;
    private String assigneeId;
    private String assigneeName;
    private String status;
    private String comment;
    private Date actionTime;
}
