package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "反馈统计")
public class AdminStatisticFeedbackVO {

    private AdminTrendVO trend;
    private List<AdminStatisticBreakdownVO> statusBreakdown;
    private long createdInRange;
    private long repliedInRange;
    private long closedInRange;
}
