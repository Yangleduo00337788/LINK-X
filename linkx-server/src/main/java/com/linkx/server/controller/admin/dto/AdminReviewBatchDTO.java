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

    @Schema(description = "用户处置：none / freeze / ban（批量通过时可带；批量驳回忽略）")
    @Size(max = 16)
    private String userAction;

    @Schema(description = "内容处置：none / delete（批量通过时可带；批量驳回忽略）")
    @Size(max = 16)
    private String contentAction;

    @Schema(description = "群处置：none / dissolve / freeze_owner / ban_owner")
    @Size(max = 32)
    private String groupAction;
}
