package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskRuleSimulateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleSimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskRuleVO;

public interface AdminRiskRuleService {

    PageResultVO<AdminRiskRuleVO> list(AdminPageQueryDTO query);

    AdminRiskRuleVO detail(Long id);

    AdminRiskRuleVO create(AdminRiskRuleDTO dto, Long operatorId);

    AdminRiskRuleVO update(Long id, AdminRiskRuleDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminRiskRuleSimulateVO simulate(AdminRiskRuleSimulateDTO dto);
}
