package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "审核处置请求")
public class AdminReviewResolveDTO {

    @Schema(description = "处理意见")
    @Size(max = 1000)
    private String resolution;
}
