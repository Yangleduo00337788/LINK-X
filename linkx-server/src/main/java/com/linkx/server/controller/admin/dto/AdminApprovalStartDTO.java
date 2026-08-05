package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发起审批")
public class AdminApprovalStartDTO {

    @NotBlank
    private String flowId;

    @NotBlank
    private String bizType;

    @NotBlank
    private String bizId;

    @NotBlank
    private String title;
}
