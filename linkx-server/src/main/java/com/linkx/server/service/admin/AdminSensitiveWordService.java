package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminSensitiveWordDTO;
import com.linkx.server.controller.admin.vo.AdminSensitiveWordVO;

public interface AdminSensitiveWordService {

    PageResultVO<AdminSensitiveWordVO> list(AdminPageQueryDTO query);

    AdminSensitiveWordVO detail(Long id);

    AdminSensitiveWordVO create(AdminSensitiveWordDTO dto, Long operatorId);

    AdminSensitiveWordVO update(Long id, AdminSensitiveWordDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);
}
