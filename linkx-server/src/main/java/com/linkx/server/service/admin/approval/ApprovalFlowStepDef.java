package com.linkx.server.service.admin.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalFlowStepDef {

    private String name;
    /** approve | countersign | cc */
    private String nodeType;
    /** user | role */
    private String assigneeType;
    /** 单个处理人/角色 ID */
    private String assigneeId;
    /** 会签多用户（可选，优先于 assigneeId） */
    private List<String> assigneeIds;
}
