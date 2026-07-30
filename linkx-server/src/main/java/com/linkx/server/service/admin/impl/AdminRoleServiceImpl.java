package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignMenuDTO;
import com.linkx.server.controller.admin.dto.AdminRoleDTO;
import com.linkx.server.controller.admin.vo.AdminPermissionVO;
import com.linkx.server.controller.admin.vo.AdminRoleVO;
import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.admin.AdminRoleMenu;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysPermissionMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.admin.AdminRoleMenuMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminRoleService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final RbacService rbacService;

    @Override
    public PageResultVO<AdminRoleVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysRole::getRoleCode).like(kw)
                        .or(SysRole::getRoleName).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysRole::getStatus).eq(query.getStatus());
        }
        qw.orderBy(SysRole::getId, true);
        long total = sysRoleMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminRoleVO> items = sysRoleMapper.selectListByQuery(qw).stream()
                .map(this::toRoleVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminRoleVO detail(Long id) {
        return toRoleVO(requireRole(id));
    }

    @Override
    @Transactional
    public Long create(AdminRoleDTO dto, Long operatorId) {
        if (isBuiltinProtectedRole(dto.getRoleCode())) {
            throw new CustomException(400, "builtin role code is reserved");
        }
        SysRole role = rbacService.createRole(dto.getRoleCode(), dto.getRoleName(), dto.getDescription(), operatorId);
        if (dto.getStatus() != null && dto.getStatus() != 1) {
            role.setStatus(dto.getStatus());
            role.setUpdateBy(operatorId);
            role.setUpdateTime(new Date());
            sysRoleMapper.update(role);
        }
        return role.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminRoleDTO dto, Long operatorId) {
        SysRole role = requireRole(id);
        if (StringUtils.hasText(dto.getRoleName())) {
            role.setRoleName(dto.getRoleName());
        }
        if (dto.getDescription() != null) {
            role.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            if (dto.getStatus() != 1 && isBuiltinProtectedRole(role.getRoleCode())) {
                throw new CustomException(400, "builtin role cannot be disabled");
            }
            role.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getRoleCode()) && !dto.getRoleCode().equals(role.getRoleCode())) {
            throw new CustomException(400, "role code cannot be changed");
        }
        role.setUpdateBy(operatorId);
        role.setUpdateTime(new Date());
        sysRoleMapper.update(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (isBuiltinProtectedRole(role.getRoleCode())) {
            throw new CustomException(400, "builtin role cannot be deleted");
        }
        sysRoleMapper.deleteById(id);
        adminRoleMenuMapper.deleteByQuery(
                QueryWrapper.create().where(AdminRoleMenu::getRoleId).eq(id));
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        requireRole(roleId);
        return adminRoleMenuMapper.selectListByQuery(
                        QueryWrapper.create().where(AdminRoleMenu::getRoleId).eq(roleId))
                .stream()
                .map(AdminRoleMenu::getMenuId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, AdminRoleAssignMenuDTO dto) {
        requireRole(roleId);
        adminRoleMenuMapper.deleteByQuery(
                QueryWrapper.create().where(AdminRoleMenu::getRoleId).eq(roleId));
        if (dto.getMenuIds() == null || dto.getMenuIds().isEmpty()) {
            return;
        }
        Date now = new Date();
        for (Long menuId : dto.getMenuIds()) {
            if (menuId == null) {
                continue;
            }
            adminRoleMenuMapper.insert(AdminRoleMenu.builder()
                    .roleId(roleId)
                    .menuId(menuId)
                    .createdAt(now)
                    .build());
        }
    }

    @Override
    public PageResultVO<AdminPermissionVO> listPermissions(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysPermission::getPermissionCode).like(kw)
                        .or(SysPermission::getPermissionName).like(kw);
            });
        }
        if (query.getStatus() != null) {
            qw.and(SysPermission::getStatus).eq(query.getStatus());
        }
        qw.orderBy(SysPermission::getId, true);
        long total = sysPermissionMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminPermissionVO> items = sysPermissionMapper.selectListByQuery(qw).stream()
                .map(p -> AdminPermissionVO.builder()
                        .id(p.getId())
                        .permissionCode(p.getPermissionCode())
                        .permissionName(p.getPermissionName())
                        .resourceType(p.getResourceType())
                        .resourcePath(p.getResourcePath())
                        .description(p.getDescription())
                        .status(p.getStatus())
                        .build())
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    private AdminRoleVO toRoleVO(SysRole role) {
        return AdminRoleVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    private SysRole requireRole(Long id) {
        SysRole role = sysRoleMapper.selectOneById(id);
        if (role == null) {
            throw new CustomException(404, "role not found");
        }
        return role;
    }

    private static boolean isBuiltinProtectedRole(String roleCode) {
        return "admin".equals(roleCode) || "super_admin".equals(roleCode) || "user".equals(roleCode);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
