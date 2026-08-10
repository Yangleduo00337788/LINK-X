package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "创建异步导出任务")
public class AdminExportJobCreateDTO {

    @NotBlank
    @Schema(description = "模块：users/devices/blacklist/risk-events/reviews/feedback/audit-logs/login-logs/statistics")
    private String module;

    @Schema(description = "与列表页一致的筛选条件")
    private Map<String, Object> query;
}
