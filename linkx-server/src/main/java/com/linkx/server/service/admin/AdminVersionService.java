package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminVersionDTO;
import com.linkx.server.controller.admin.dto.AdminVersionQueryDTO;
import com.linkx.server.controller.admin.vo.AdminVersionVO;

public interface AdminVersionService {

    PageResultVO<AdminVersionVO> list(AdminVersionQueryDTO query);

    AdminVersionVO detail(Long id);

    AdminVersionVO create(AdminVersionDTO dto, Long operatorId);

    AdminVersionVO update(Long id, AdminVersionDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminVersionVO publish(Long id, Long operatorId);
}
