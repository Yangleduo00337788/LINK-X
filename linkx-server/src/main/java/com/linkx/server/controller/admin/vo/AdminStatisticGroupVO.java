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
@Schema(description = "群活跃度统计")
public class AdminStatisticGroupVO {

    @Schema(description = "群总量")
    private long totalGroups;

    @Schema(description = "区间内有消息的活跃群数")
    private long activeGroupsInRange;

    @Schema(description = "区间新建群数")
    private long newGroupsInRange;

    @Schema(description = "区间群消息量")
    private long groupMessagesInRange;

    @Schema(description = "趋势：新建群 / 群消息")
    private AdminTrendVO trend;

    @Schema(description = "区间消息量 Top 群")
    private List<AdminGroupActivityItemVO> topGroups;
}
