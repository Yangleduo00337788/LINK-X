package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminDutyScheduleDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDutyScheduleVO;

public interface AdminDutyScheduleService {

    PageResultVO<AdminDutyScheduleVO> list(AdminPageQueryDTO query);

    AdminDutyScheduleVO detail(Long id);

    AdminDutyScheduleVO create(AdminDutyScheduleDTO dto, Long operatorId);

    AdminDutyScheduleVO update(Long id, AdminDutyScheduleDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);
}
