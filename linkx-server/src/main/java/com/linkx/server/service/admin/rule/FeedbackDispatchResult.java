package com.linkx.server.service.admin.rule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeedbackDispatchResult {

    private Long ruleId;
    private String ruleName;
    private Long assigneeId;
    private String actionType;
    private String assigneeSource;
    private boolean assigned;
    private boolean notified;
    private String notifyRoles;
    private String notifyChannels;
}
