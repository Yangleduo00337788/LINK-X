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
@Schema(description = "用户统计")
public class AdminStatisticUserVO {

    private AdminTrendVO trend;
    private List<AdminStatisticBreakdownVO> statusBreakdown;
    private long newUsersInRange;
    private long loginSuccessInRange;
    private long loginFailInRange;
}
