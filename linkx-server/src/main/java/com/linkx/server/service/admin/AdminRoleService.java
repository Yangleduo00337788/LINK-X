package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignMenuDTO;
import com.linkx.server.controller.admin.dto.AdminRoleDTO;
import com.linkx.server.controller.admin.vo.AdminPermissionVO;
import com.linkx.server.controller.admin.vo.AdminRoleVO;

import java.util.List;

public interface AdminRoleService {

    PageResultVO<AdminRoleVO> list(AdminPageQueryDTO query);

    AdminRoleVO detail(Long id);

    Long create(AdminRoleDTO dto, Long operatorId);

    void update(Long id, AdminRoleDTO dto, Long operatorId);

    void delete(Long id);

    List<Long> getRoleMenuIds(Long roleId);

    void assignMenus(Long roleId, AdminRoleAssignMenuDTO dto);

    PageResultVO<AdminPermissionVO> listPermissions(AdminPageQueryDTO query);
}
