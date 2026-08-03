package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "反馈指派")
public class AdminFeedbackAssignDTO {

    @Schema(description = "处理人用户ID，null 表示取消指派")
    private Long assigneeId;
}
