package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchRuleDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackDispatchSimulateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchRuleVO;
import com.linkx.server.controller.admin.vo.AdminFeedbackDispatchSimulateVO;

public interface AdminFeedbackDispatchRuleService {

    PageResultVO<AdminFeedbackDispatchRuleVO> list(AdminPageQueryDTO query);

    AdminFeedbackDispatchRuleVO detail(Long id);

    AdminFeedbackDispatchRuleVO create(AdminFeedbackDispatchRuleDTO dto, Long operatorId);

    AdminFeedbackDispatchRuleVO update(Long id, AdminFeedbackDispatchRuleDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminFeedbackDispatchSimulateVO simulate(AdminFeedbackDispatchSimulateDTO dto);
}
