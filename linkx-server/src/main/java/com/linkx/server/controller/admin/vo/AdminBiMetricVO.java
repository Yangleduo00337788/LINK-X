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
@Schema(description = "BI 指标元数据")
public class AdminBiMetricVO {

    private String key;
    private String name;
    private List<String> dimensions;
    private AdminBiDrillTargetVO drillTarget;
}
