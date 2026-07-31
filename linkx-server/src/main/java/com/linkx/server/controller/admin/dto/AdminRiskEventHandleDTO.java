package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "风险事件处置请求")
public class AdminRiskEventHandleDTO {

    @NotBlank
    @Schema(description = "处置动作：handled/ignored", example = "handled")
    private String action;

    @Size(max = 1000)
    @Schema(description = "处置意见")
    private String resolution;

    @Schema(description = "连带用户处置：none/freeze/ban，仅 handled 时生效", example = "none")
    private String userAction;
}
