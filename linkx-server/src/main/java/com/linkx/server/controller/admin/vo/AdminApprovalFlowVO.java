package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "审批流程")
public class AdminApprovalFlowVO {

    private String id;
    private String name;
    private String bizType;
    private String description;
    private String stepsJson;
    private Boolean enabled;
    private Boolean autoStart;
    private Integer priority;
    private Date createTime;
    private Date updateTime;
}
