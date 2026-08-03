package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminAbnormalAccessQueryDTO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessSummaryVO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessVO;

import java.util.List;

public interface AdminAbnormalAccessService {

    PageResultVO<AdminAbnormalAccessVO> list(AdminAbnormalAccessQueryDTO query);

    List<AdminAbnormalAccessVO> listForExport(AdminAbnormalAccessQueryDTO query);

    AdminAbnormalAccessSummaryVO summary();
}
