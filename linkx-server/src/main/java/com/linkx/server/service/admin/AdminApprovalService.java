package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminApprovalActionDTO;
import com.linkx.server.controller.admin.dto.AdminApprovalStartDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalInboxItemVO;
import com.linkx.server.controller.admin.vo.AdminApprovalInstanceVO;

public interface AdminApprovalService {

    PageResultVO<AdminApprovalInboxItemVO> inbox(AdminPageQueryDTO query, Long operatorId);

    PageResultVO<AdminApprovalInboxItemVO> ccInbox(AdminPageQueryDTO query, Long operatorId);

    AdminApprovalInstanceVO start(AdminApprovalStartDTO dto, Long operatorId);

    AdminApprovalInstanceVO instanceDetail(Long instanceId, Long operatorId);

    void approve(Long recordId, AdminApprovalActionDTO dto, Long operatorId);

    void reject(Long recordId, AdminApprovalActionDTO dto, Long operatorId);
}
