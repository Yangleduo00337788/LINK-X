package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审批处理")
public class AdminApprovalActionDTO {

    private String comment;
}
