package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量审核请求")
public class AdminReviewBatchDTO {

    @Schema(description = "审核任务 ID 列表")
    @NotEmpty
    private List<Long> ids;

    @Schema(description = "动作：approve / reject")
    @NotBlank
    @Size(max = 16)
    private String action;

    @Schema(description = "处理意见")
    @Size(max = 1000)
    private String resolution;
}
