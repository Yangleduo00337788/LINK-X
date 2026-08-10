package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.vo.AdminSystemMonitorOverviewVO;
import com.linkx.server.controller.admin.vo.AdminSystemTableStatsVO;

public interface AdminSystemMonitorService {

    /** 轻量总览：不含 information_schema 全表扫描 */
    AdminSystemMonitorOverviewVO overview();

    /** 数据库表体量（较重，默认走服务端缓存） */
    AdminSystemTableStatsVO tableStats(boolean refresh);
}
