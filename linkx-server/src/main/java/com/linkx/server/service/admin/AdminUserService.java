package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserUpdateDTO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminUserDetailVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import com.linkx.server.controller.admin.dto.AdminUserResetPasswordDTO;
import com.linkx.server.controller.admin.vo.AdminUserResetPasswordVO;
import com.linkx.server.controller.vo.DeviceVO;

import java.util.List;

public interface AdminUserService {

    PageResultVO<AdminUserListVO> list(AdminUserQueryDTO query);

    List<AdminUserListVO> listForExport(AdminUserQueryDTO query);

    AdminUserDetailVO detail(Long id);

    void update(Long id, AdminUserUpdateDTO dto, Long operatorId);

    void freeze(Long id, AdminUserActionDTO dto, Long operatorId);

    void unfreeze(Long id, Long operatorId);

    void ban(Long id, AdminUserActionDTO dto, Long operatorId);

    void unban(Long id, Long operatorId);

    AdminUserResetPasswordVO resetPassword(Long id, AdminUserResetPasswordDTO dto, Long operatorId);

    List<DeviceVO> devices(Long id);

    PageResultVO<AdminLoginLogVO> logins(Long id, AdminPageQueryDTO query);

    void setDeviceBindingEnabled(Long id, boolean enabled, Long operatorId, String ip, String userAgent);

    void approveDevice(Long id, String deviceId, String deviceName, Long operatorId, String ip, String userAgent);

    void revokeDeviceApproval(Long id, String deviceId, Long operatorId, String ip, String userAgent);
}
