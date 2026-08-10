package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "反馈分流规则模拟结果")
public class AdminFeedbackDispatchSimulateVO {

    private boolean matched;
    private Long ruleId;
    private String ruleName;
    private Long assigneeId;
    private String assigneeName;
    private String actionType;
    private String assigneeSource;
    private String notifyRoles;
    private String notifyChannels;
}
