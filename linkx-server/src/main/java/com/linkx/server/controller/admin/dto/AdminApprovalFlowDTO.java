package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "审批流程定义")
public class AdminApprovalFlowDTO {

    @NotBlank
    private String name;

    @NotBlank
    @Schema(description = "review|feedback|generic")
    private String bizType;

    private String description;

    @NotBlank
    @Schema(description = "步骤 JSON 数组")
    private String stepsJson;

    private Boolean enabled;
    private Boolean autoStart;
    private Integer priority;
}
