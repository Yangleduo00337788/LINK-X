package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;

import java.util.List;

public interface AdminAuditLogService {

    PageResultVO<AdminOperationLogVO> listAuditLogs(AdminPageQueryDTO query);

    /** 导出用操作日志（最多 EXPORT_MAX_SIZE） */
    List<AdminOperationLogVO> listAuditLogsForExport(AdminPageQueryDTO query);

    AdminOperationLogVO auditDetail(Long id);

    PageResultVO<AdminLoginLogVO> listLoginLogs(AdminPageQueryDTO query);

    /** 导出用登录日志（最多 EXPORT_MAX_SIZE） */
    List<AdminLoginLogVO> listLoginLogsForExport(AdminPageQueryDTO query);

    AdminLoginLogVO loginDetail(Long id);
}
