package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.vo.AdminActivityHeatmapVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticGroupVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;

public interface AdminStatisticsService {

    AdminStatisticOverviewVO overview(int days);

    AdminStatisticUserVO users(int days);

    AdminStatisticContentVO content(int days);

    AdminStatisticRiskVO risk(int days);

    AdminStatisticFeedbackVO feedback(int days);

    /** 群活跃度：新建/消息趋势 + Top 活跃群 */
    AdminStatisticGroupVO groups(int days);

    /**
     * 活跃时段热力图（星期 × 小时）。
     *
     * @param metric logins（默认）或 messages
     */
    AdminActivityHeatmapVO activityHeatmap(int days, String metric);

    /** 仪表盘趋势：新增用户 + 消息量 + 登录成功 */
    AdminTrendVO dashboardTrends(int days);

    long countOnlineDevices();

    long countRiskEventsSince(java.util.Date since);
}
