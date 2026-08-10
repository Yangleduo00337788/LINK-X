package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminApprovalFlowDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalFlowVO;

public interface AdminApprovalFlowService {

    PageResultVO<AdminApprovalFlowVO> list(AdminPageQueryDTO query);

    AdminApprovalFlowVO detail(Long id);

    AdminApprovalFlowVO create(AdminApprovalFlowDTO dto, Long operatorId);

    AdminApprovalFlowVO update(Long id, AdminApprovalFlowDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);
}
