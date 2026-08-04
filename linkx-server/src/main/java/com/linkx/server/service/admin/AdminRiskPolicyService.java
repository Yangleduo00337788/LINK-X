package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminRiskPolicySimulateDTO;
import com.linkx.server.controller.admin.dto.AdminRiskPolicyUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicySimulateVO;
import com.linkx.server.controller.admin.vo.AdminRiskPolicyVO;
import com.linkx.server.controller.admin.vo.AdminReviewRiskContextVO;
import com.linkx.server.entity.admin.SysReviewTask;

public interface AdminRiskPolicyService {

    AdminRiskPolicyVO getOverview();

    AdminRiskPolicyVO update(AdminRiskPolicyUpdateDTO dto, Long operatorId);

    AdminRiskPolicySimulateVO simulate(AdminRiskPolicySimulateDTO dto);
}
