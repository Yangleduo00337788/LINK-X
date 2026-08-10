package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "内容统计")
public class AdminStatisticContentVO {

    private AdminTrendVO trend;
    private long messagesInRange;
    private long momentsInRange;
    private long uploadsInRange;
}
