package com.linkx.server.service.admin.impl;

import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.DataScopeType;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPermissionDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignMenuDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignUserDTO;
import com.linkx.server.controller.admin.dto.AdminRoleDTO;
import com.linkx.server.controller.admin.vo.AdminPermissionVO;
import com.linkx.server.controller.admin.vo.AdminRoleUserVO;
import com.linkx.server.controller.admin.vo.AdminRoleVO;
import com.linkx.server.entity.SysDept;
import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysRoleDept;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.AdminRoleMenu;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysPermissionMapper;
import com.linkx.server.mapper.SysRoleDeptMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.AdminRoleMenuMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminRoleService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysRoleDeptMapper sysRoleDeptMapper;
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
        boolean dirty = false;
        if (dto.getStatus() != null && dto.getStatus() != 1) {
            role.setStatus(dto.getStatus());
            dirty = true;
        }
        if (dto.getDataScope() != null) {
            if (!DataScopeType.isValid(dto.getDataScope())) {
                throw new CustomException(400, "invalid dataScope");
            }
            role.setDataScope(dto.getDataScope());
            dirty = true;
        }
        if (dirty) {
            role.setUpdateBy(operatorId);
            role.setUpdateTime(new Date());
            sysRoleMapper.update(role);
        }
        syncRoleDepts(role.getId(), role.getDataScope(), dto.getDeptIds());
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
        if (dto.getDataScope() != null) {
            if (!DataScopeType.isValid(dto.getDataScope())) {
                throw new CustomException(400, "invalid dataScope");
            }
            if (isBuiltinProtectedRole(role.getRoleCode()) && dto.getDataScope() != DataScopeType.ALL) {
                throw new CustomException(400, "builtin admin role must keep dataScope=ALL");
            }
            role.setDataScope(dto.getDataScope());
        }
        role.setUpdateBy(operatorId);
        role.setUpdateTime(new Date());
        sysRoleMapper.update(role);
        Integer scope = role.getDataScope();
        if (dto.getDataScope() != null || dto.getDeptIds() != null || scope == DataScopeType.CUSTOM) {
            syncRoleDepts(id, scope, dto.getDeptIds());
        }
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
        clearRoleDepts(id);
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
    public List<AdminRoleUserVO> listRoleUsers(Long roleId) {
        requireRole(roleId);
        List<SysUserRole> links = sysUserRoleMapper.selectListByQuery(
                QueryWrapper.create().where(SysUserRole::getRoleId).eq(roleId));
        if (links.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = links.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        List<SysUser> users = sysUserMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(userIds));
        return users.stream()
                .map(u -> AdminRoleUserVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .status(u.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignUsers(Long roleId, AdminRoleAssignUserDTO dto, Long operatorId) {
        SysRole role = requireRole(roleId);
        if ("user".equals(role.getRoleCode())) {
            throw new CustomException(400, "default user role cannot be reassigned here");
        }
        List<Long> targetIds = dto.getUserIds() == null ? List.of() : dto.getUserIds().stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        List<SysUserRole> existing = sysUserRoleMapper.selectListByQuery(
                QueryWrapper.create().where(SysUserRole::getRoleId).eq(roleId));
        Set<Long> oldUserIds = existing.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        Set<Long> newUserIds = new HashSet<>(targetIds);

        // 超管角色至少保留 1 个用户
        if ("super_admin".equals(role.getRoleCode()) && newUserIds.isEmpty()) {
            throw new CustomException(400, "super_admin must keep at least one user");
        }

        for (Long uid : oldUserIds) {
            if (!newUserIds.contains(uid)) {
                sysUserRoleMapper.deleteByQuery(QueryWrapper.create()
                        .where(SysUserRole::getRoleId).eq(roleId)
                        .and(SysUserRole::getUserId).eq(uid));
                rbacService.evictUserCache(uid);
            }
        }
        Date now = new Date();
        for (Long uid : newUserIds) {
            if (oldUserIds.contains(uid)) {
                continue;
            }
            SysUser user = sysUserMapper.selectOneById(uid);
            if (user == null) {
                throw new CustomException(404, "user not found: " + uid);
            }
            try {
                sysUserRoleMapper.insert(SysUserRole.builder()
                        .userId(uid)
                        .roleId(roleId)
                        .createBy(operatorId)
                        .deleted(0)
                        .createTime(now)
                        .build());
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 幂等
            }
            rbacService.evictUserCache(uid);
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
                .map(this::toPermissionVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminPermissionVO permissionDetail(Long id) {
        return toPermissionVO(requirePermission(id));
    }

    @Override
    @Transactional
    public Long createPermission(AdminPermissionDTO dto) {
        String code = dto.getPermissionCode().trim();
        long exists = sysPermissionMapper.selectCountByQuery(
                QueryWrapper.create().where(SysPermission::getPermissionCode).eq(code));
        if (exists > 0) {
            throw new CustomException(409, "permission code already exists");
        }
        Date now = new Date();
        SysPermission perm = SysPermission.builder()
                .permissionCode(code)
                .permissionName(dto.getPermissionName().trim())
                .resourceType(StringUtils.hasText(dto.getResourceType()) ? dto.getResourceType().trim() : "button")
                .resourcePath(dto.getResourcePath())
                .description(dto.getDescription())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        sysPermissionMapper.insert(perm);
        return perm.getId();
    }

    @Override
    @Transactional
    public void updatePermission(Long id, AdminPermissionDTO dto) {
        SysPermission perm = requirePermission(id);
        if (StringUtils.hasText(dto.getPermissionCode())
                && !dto.getPermissionCode().trim().equals(perm.getPermissionCode())) {
            throw new CustomException(400, "permission code cannot be changed");
        }
        if (StringUtils.hasText(dto.getPermissionName())) {
            perm.setPermissionName(dto.getPermissionName().trim());
        }
        if (dto.getResourceType() != null) {
            perm.setResourceType(dto.getResourceType().trim());
        }
        if (dto.getResourcePath() != null) {
            perm.setResourcePath(dto.getResourcePath());
        }
        if (dto.getDescription() != null) {
            perm.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            perm.setStatus(dto.getStatus());
        }
        perm.setUpdateTime(new Date());
        sysPermissionMapper.update(perm);
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        requirePermission(id);
        sysPermissionMapper.deleteById(id);
    }

    private AdminPermissionVO toPermissionVO(SysPermission p) {
        return AdminPermissionVO.builder()
                .id(p.getId())
                .permissionCode(p.getPermissionCode())
                .permissionName(p.getPermissionName())
                .resourceType(p.getResourceType())
                .resourcePath(p.getResourcePath())
                .description(p.getDescription())
                .status(p.getStatus())
                .build();
    }

    private SysPermission requirePermission(Long id) {
        SysPermission perm = sysPermissionMapper.selectOneById(id);
        if (perm == null) {
            throw new CustomException(404, "permission not found");
        }
        return perm;
    }

    private AdminRoleVO toRoleVO(SysRole role) {
        Integer dataScope = role.getDataScope();
        if (!DataScopeType.isValid(dataScope)) {
            dataScope = DataScopeType.ALL;
        }
        return AdminRoleVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus())
                .dataScope(dataScope)
                .deptIds(listRoleDeptIds(role.getId()))
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    private void syncRoleDepts(Long roleId, Integer dataScope, List<Long> deptIds) {
        int scope = DataScopeType.isValid(dataScope) ? dataScope : DataScopeType.ALL;
        if (scope != DataScopeType.CUSTOM) {
            clearRoleDepts(roleId);
            return;
        }
        if (deptIds == null) {
            if (listRoleDeptIds(roleId).isEmpty()) {
                throw new CustomException(400, "custom dataScope requires at least one department");
            }
            return;
        }
        List<Long> cleaned = normalizeDeptIds(deptIds);
        if (cleaned.isEmpty()) {
            throw new CustomException(400, "custom dataScope requires at least one department");
        }
        clearRoleDepts(roleId);
        Date now = new Date();
        for (Long deptId : cleaned) {
            sysRoleDeptMapper.insert(SysRoleDept.builder()
                    .roleId(roleId)
                    .deptId(deptId)
                    .createTime(now)
                    .build());
        }
    }

    private List<Long> normalizeDeptIds(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        List<Long> cleaned = deptIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return List.of();
        }
        Set<Long> existing = sysDeptMapper.selectListByQuery(
                        QueryWrapper.create().where(SysDept::getId).in(cleaned))
                .stream()
                .map(SysDept::getId)
                .collect(Collectors.toSet());
        for (Long id : cleaned) {
            if (!existing.contains(id)) {
                throw new CustomException(400, "department not found: " + id);
            }
        }
        return cleaned;
    }

    private void clearRoleDepts(Long roleId) {
        sysRoleDeptMapper.deleteByQuery(
                QueryWrapper.create().where(SysRoleDept::getRoleId).eq(roleId));
    }

    private List<Long> listRoleDeptIds(Long roleId) {
        return sysRoleDeptMapper.selectListByQuery(
                        QueryWrapper.create().where(SysRoleDept::getRoleId).eq(roleId))
                .stream()
                .map(SysRoleDept::getDeptId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
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
