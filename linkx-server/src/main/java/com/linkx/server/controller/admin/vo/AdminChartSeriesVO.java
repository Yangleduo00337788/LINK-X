package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "图表单条序列")
public class AdminChartSeriesVO {

    @Schema(description = "序列显示名")
    private String name;

    @Schema(description = "序列键，便于前端 i18n")
    private String key;

    @Schema(description = "按 labels 对齐的数值")
    private List<Long> data;
}
