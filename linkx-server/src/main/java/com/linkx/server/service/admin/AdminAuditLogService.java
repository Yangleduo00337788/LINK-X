package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminAuditLogQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;

import java.util.List;

public interface AdminAuditLogService {

    PageResultVO<AdminOperationLogVO> listAuditLogs(AdminAuditLogQueryDTO query);

    /** 导出用操作日志（最多 EXPORT_MAX_SIZE） */
    List<AdminOperationLogVO> listAuditLogsForExport(AdminAuditLogQueryDTO query);

    AdminOperationLogVO auditDetail(Long id);

    PageResultVO<AdminLoginLogVO> listLoginLogs(AdminPageQueryDTO query);

    /** 导出用登录日志（最多 EXPORT_MAX_SIZE） */
    List<AdminLoginLogVO> listLoginLogsForExport(AdminPageQueryDTO query);

    AdminLoginLogVO loginDetail(Long id);
}
