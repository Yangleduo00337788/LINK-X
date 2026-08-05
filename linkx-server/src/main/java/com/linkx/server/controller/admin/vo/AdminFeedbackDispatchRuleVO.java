package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "反馈分流规则")
public class AdminFeedbackDispatchRuleVO {

    private Long id;
    private String name;
    private String feedbackType;
    private String keyword;
    private String conditionJson;
    private Long assigneeId;
    private String assigneeName;
    private String assigneeSource;
    private Long dutyScheduleId;
    private String dutyScheduleName;
    private String actionType;
    private String actionConfig;
    private String notifyRoles;
    private String notifyChannels;
    private Integer priority;
    private Boolean enabled;
    private Date createTime;
    private Date updateTime;
}
