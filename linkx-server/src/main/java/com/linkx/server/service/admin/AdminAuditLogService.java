package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;

public interface AdminAuditLogService {

    PageResultVO<AdminOperationLogVO> listAuditLogs(AdminPageQueryDTO query);

    AdminOperationLogVO auditDetail(Long id);

    PageResultVO<AdminLoginLogVO> listLoginLogs(AdminPageQueryDTO query);

    AdminLoginLogVO loginDetail(Long id);
}
