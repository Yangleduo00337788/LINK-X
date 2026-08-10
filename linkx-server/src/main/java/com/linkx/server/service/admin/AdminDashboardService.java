package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.vo.AdminDashboardRealtimeVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminPendingTaskVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;

import java.util.List;

public interface AdminDashboardService {

    AdminDashboardSummaryVO summary(Long operatorUserId);

    AdminTrendVO trends(int days);

    AdminDashboardRealtimeVO realtime(Long operatorUserId);

    List<AdminPendingTaskVO> pendingTasks(Long operatorUserId);
}
