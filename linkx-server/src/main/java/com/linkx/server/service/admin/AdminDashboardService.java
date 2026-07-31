package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.vo.AdminDashboardRealtimeVO;
import com.linkx.server.controller.admin.vo.AdminDashboardSummaryVO;
import com.linkx.server.controller.admin.vo.AdminPendingTaskVO;
import com.linkx.server.controller.admin.vo.AdminTrendVO;

import java.util.List;

public interface AdminDashboardService {

    AdminDashboardSummaryVO summary();

    AdminTrendVO trends(int days);

    AdminDashboardRealtimeVO realtime();

    List<AdminPendingTaskVO> pendingTasks();
}
