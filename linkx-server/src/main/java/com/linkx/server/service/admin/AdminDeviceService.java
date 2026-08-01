package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;

import java.util.List;

public interface AdminDeviceService {

    PageResultVO<AdminDeviceVO> list(AdminDeviceQueryDTO query);

    List<AdminDeviceVO> listForExport(AdminDeviceQueryDTO query);

    void kick(Long userId, String deviceId, Long operatorId, String operatorUsername, String ip, String userAgent);
}
