package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminExportJobCreateDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminExportJobVO;
import com.linkx.server.entity.admin.SysAdminExportJob;

public interface AdminExportJobService {

    AdminExportJobVO create(AdminExportJobCreateDTO dto, Long requesterId);

    AdminExportJobVO detail(Long id, Long requesterId);

    PageResultVO<AdminExportJobVO> list(AdminPageQueryDTO query, Long requesterId);

    SysAdminExportJob loadDownloadable(Long id, Long requesterId);

    void expireStaleJobs();
}
