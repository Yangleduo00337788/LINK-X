package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminBiQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBiMetricVO;
import com.linkx.server.controller.admin.vo.AdminBiQueryVO;
import com.linkx.server.controller.admin.vo.AdminBigScreenVO;

import java.util.List;

public interface AdminBiService {

    List<AdminBiMetricVO> listMetrics();

    AdminBiQueryVO query(AdminBiQueryDTO dto);

    AdminBigScreenVO bigScreenData();
}
