package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘待处理任务")
public class AdminPendingTaskVO {

    @Schema(description = "任务类型：feedback / review / risk")
    private String type;

    @Schema(description = "展示标题键或文案")
    private String title;

    @Schema(description = "待处理数量")
    private long count;

    @Schema(description = "跳转路径")
    private String path;
}
