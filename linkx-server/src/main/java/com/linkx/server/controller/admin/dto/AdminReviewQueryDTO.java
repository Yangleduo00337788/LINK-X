package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审核任务列表查询")
public class AdminReviewQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "审核状态：pending/approved/rejected")
    private String reviewStatus;

    @Schema(description = "来源：report/sensitive/manual")
    private String sourceType;
}
