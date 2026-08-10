package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "分类占比项")
public class AdminStatisticBreakdownVO {

    @Schema(description = "分类键")
    private String key;

    @Schema(description = "显示名")
    private String name;

    @Schema(description = "数量")
    private long value;
}
