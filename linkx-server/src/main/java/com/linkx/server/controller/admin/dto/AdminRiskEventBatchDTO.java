package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量处置风险事件请求")
public class AdminRiskEventBatchDTO {

    @Schema(description = "风险事件 ID 列表")
    @NotEmpty
    private List<Long> ids;

    @NotBlank
    @Size(max = 16)
    @Schema(description = "处置动作：handled/ignored", example = "handled")
    private String action;

    @Size(max = 1000)
    @Schema(description = "处置意见")
    private String resolution;
}
