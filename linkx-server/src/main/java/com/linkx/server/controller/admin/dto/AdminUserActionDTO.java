package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "冻结/封禁原因")
public class AdminUserActionDTO {

    @Schema(description = "操作原因")
    @Size(max = 255)
    private String reason;
}
