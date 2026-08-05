package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "审批实例")
public class AdminApprovalInstanceVO {

    private String id;
    private String flowId;
    private String flowName;
    private String bizType;
    private String bizId;
    private String title;
    private String status;
    private Integer currentStep;
    private String applicantId;
    private String applicantName;
    private Date finishedAt;
    private Date createTime;
    private List<AdminApprovalTimelineItemVO> timeline;
}
