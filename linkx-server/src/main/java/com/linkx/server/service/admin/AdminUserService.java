package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.vo.DeviceVO;

import java.util.List;

public interface AdminUserService {

    PageResultVO<AdminUserListVO> list(AdminUserQueryDTO query);

    AdminUserDetailVO detail(Long id);

    void update(Long id, AdminUserUpdateDTO dto, Long operatorId);

    void freeze(Long id, AdminUserActionDTO dto, Long operatorId);

    void unfreeze(Long id, Long operatorId);

    void ban(Long id, AdminUserActionDTO dto, Long operatorId);

    void unban(Long id, Long operatorId);

    List<DeviceVO> devices(Long id);
}
