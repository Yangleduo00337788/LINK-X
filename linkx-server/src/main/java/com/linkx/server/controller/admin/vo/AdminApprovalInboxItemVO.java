package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "审批待办")
public class AdminApprovalInboxItemVO {

    private String recordId;
    private String instanceId;
    private String title;
    private String flowName;
    private String bizType;
    private String bizId;
    private String stepName;
    private String nodeType;
    private String status;
    private String applicantName;
    private Date createTime;
}
