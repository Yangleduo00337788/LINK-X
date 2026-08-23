package com.linkx.server.mapper;


import com.linkx.server.mapper.row.AdminOverviewCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

@Mapper
public interface AdminStatisticsSqlMapper {

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM sys_user WHERE deleted = 0) AS totalUsers,
              (SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND update_time >= #{weekAgo}) AS activeUsers,
              (SELECT COUNT(*) FROM sys_feedback WHERE status = 'pending') AS pendingFeedback,
              (SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND create_time >= #{todayStart}) AS todayNewUsers,
              (SELECT COUNT(*) FROM im_message WHERE deleted = 0 AND create_time >= #{todayStart}) AS todayMessages,
              (SELECT COUNT(*) FROM sys_login_audit WHERE success = 1 AND create_time >= #{todayStart}) AS todayLogins,
              (SELECT COUNT(*) FROM im_message WHERE deleted = 0) AS totalMessages,
              (SELECT COUNT(*) FROM cloud_file WHERE deleted = 0) AS totalUploads,
              (SELECT COUNT(*) FROM sys_feedback WHERE status = 'closed') AS closedFeedback
            """)
    AdminOverviewCountRow selectOverviewCounts(@Param("todayStart") Date todayStart,
                                               @Param("weekAgo") Date weekAgo);
}
