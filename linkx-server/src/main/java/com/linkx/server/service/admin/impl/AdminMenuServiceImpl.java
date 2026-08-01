package com.linkx.server.service.admin.impl;

import com.linkx.server.common.RbacConstants;
import com.linkx.server.controller.admin.dto.AdminMenuDTO;
import com.linkx.server.controller.admin.dto.AdminMenuReorderDTO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminMenuVO;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.admin.AdminMenu;
import com.linkx.server.entity.admin.AdminRoleMenu;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.AdminMenuMapper;
import com.linkx.server.mapper.admin.AdminRoleMenuMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminMenuService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMenuServiceImpl implements AdminMenuService {

    private final AdminMenuMapper adminMenuMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final RbacService rbacService;

    @Override
    public List<AdminMenuTreeVO> treeAll() {
        // 管理页需看到停用菜单，便于编辑/启停
        return buildTree(listAllMenus());
    }

    @Override
    public List<AdminMenuTreeVO> treeForUser(Long userId) {
        List<AdminMenu> all = listEnabledMenus();
        List<String> perms = rbacService.getUserPermissionCodes(userId);
        if (perms.contains(RbacConstants.PERM_ALL)) {
            return buildTree(all);
        }

        Set<Long> allowedIds = resolveAllowedMenuIds(userId, all, new HashSet<>(perms));
        List<AdminMenu> filtered = all.stream()
                .filter(m -> allowedIds.contains(m.getId()))
                .collect(Collectors.toList());
        return buildTree(filtered);
    }

    @Override
    public AdminMenuVO detail(Long id) {
        AdminMenu menu = requireMenu(id);
        return toVO(menu);
    }

    @Override
    @Transactional
    public Long create(AdminMenuDTO dto, Long operatorId) {
        AdminMenu existing = adminMenuMapper.selectOneByQuery(
                QueryWrapper.create().where(AdminMenu::getName).eq(dto.getName()));
        if (existing != null) {
            throw new CustomException(409, "菜单标识已存在");
        }
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId > 0) {
            requireMenu(parentId);
        }
        AdminMenu menu = AdminMenu.builder()
                .parentId(parentId)
                .name(dto.getName())
                .title(dto.getTitle())
                .path(dto.getPath())
                .component(dto.getComponent())
                .redirect(dto.getRedirect())
                .icon(dto.getIcon())
                .menuType(dto.getMenuType())
                .permissionCode(dto.getPermissionCode())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .hidden(dto.getHidden() == null ? 0 : dto.getHidden())
                .cacheable(1)
                .externalLink(0)
                .keepAlive(1)
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .remark(dto.getRemark())
                .createdBy(operatorId)
                .createdAt(new Date())
                .updatedBy(operatorId)
                .updatedAt(new Date())
                .deleted(0)
                .build();
        adminMenuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminMenuDTO dto, Long operatorId) {
        AdminMenu menu = requireMenu(id);
        if (dto.getName() != null && !dto.getName().equals(menu.getName())) {
            AdminMenu conflict = adminMenuMapper.selectOneByQuery(
                    QueryWrapper.create().where(AdminMenu::getName).eq(dto.getName()));
            if (conflict != null && !Objects.equals(conflict.getId(), id)) {
                throw new CustomException(409, "菜单标识已存在");
            }
            menu.setName(dto.getName());
        }
        if (dto.getParentId() != null) {
            Long newParent = dto.getParentId();
            if (Objects.equals(newParent, id)) {
                throw new CustomException(400, "父菜单不能是自己");
            }
            if (newParent > 0 && isDescendant(id, newParent)) {
                throw new CustomException(400, "不能将菜单移动到其子节点下");
            }
            if (newParent > 0) {
                requireMenu(newParent);
            }
            menu.setParentId(newParent);
        }
        if (dto.getTitle() != null) {
            menu.setTitle(dto.getTitle());
        }
        if (dto.getPath() != null) {
            menu.setPath(dto.getPath());
        }
        if (dto.getComponent() != null) {
            menu.setComponent(dto.getComponent());
        }
        if (dto.getRedirect() != null) {
            menu.setRedirect(dto.getRedirect());
        }
        if (dto.getIcon() != null) {
            menu.setIcon(dto.getIcon());
        }
        if (dto.getMenuType() != null) {
            menu.setMenuType(dto.getMenuType());
        }
        if (dto.getPermissionCode() != null) {
            menu.setPermissionCode(dto.getPermissionCode());
        }
        if (dto.getSortOrder() != null) {
            menu.setSortOrder(dto.getSortOrder());
        }
        if (dto.getHidden() != null) {
            menu.setHidden(dto.getHidden());
        }
        if (dto.getStatus() != null) {
            menu.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            menu.setRemark(dto.getRemark());
        }
        menu.setUpdatedBy(operatorId);
        menu.setUpdatedAt(new Date());
        adminMenuMapper.update(menu);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        long children = adminMenuMapper.selectCountByQuery(
                QueryWrapper.create().where(AdminMenu::getParentId).eq(id));
        if (children > 0) {
            throw new CustomException(400, "请先删除子菜单");
        }
        adminMenuMapper.deleteById(id);
        adminRoleMenuMapper.deleteByQuery(
                QueryWrapper.create().where(AdminRoleMenu::getMenuId).eq(id));
    }

    @Override
    @Transactional
    public void reorder(AdminMenuReorderDTO dto, Long operatorId) {
        Date now = new Date();
        for (AdminMenuReorderDTO.Item item : dto.getItems()) {
            AdminMenu menu = requireMenu(item.getId());
            if (item.getParentId() != null) {
                Long newParent = item.getParentId();
                if (Objects.equals(newParent, item.getId())) {
                    throw new CustomException(400, "父菜单不能是自己");
                }
                if (newParent > 0 && isDescendant(item.getId(), newParent)) {
                    throw new CustomException(400, "不能将菜单移动到其子节点下");
                }
                if (newParent > 0) {
                    requireMenu(newParent);
                }
                menu.setParentId(newParent);
            }
            menu.setSortOrder(item.getSortOrder());
            menu.setUpdatedBy(operatorId);
            menu.setUpdatedAt(now);
            adminMenuMapper.update(menu);
        }
    }

    private Set<Long> resolveAllowedMenuIds(Long userId, List<AdminMenu> all, Set<String> perms) {
        Set<Long> allowed = new HashSet<>();

        List<SysRole> roles = rbacService.getUserRoles(userId);
        if (!roles.isEmpty()) {
            List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
            List<AdminRoleMenu> bindings = adminRoleMenuMapper.selectListByQuery(
                    QueryWrapper.create().where(AdminRoleMenu::getRoleId).in(roleIds));
            for (AdminRoleMenu binding : bindings) {
                allowed.add(binding.getMenuId());
            }
        }

        for (AdminMenu menu : all) {
            String code = menu.getPermissionCode();
            if (code != null && !code.isBlank() && perms.contains(code)) {
                allowed.add(menu.getId());
            }
        }

        // 补齐父级目录，保证树可渲染
        Map<Long, AdminMenu> byId = all.stream().collect(Collectors.toMap(AdminMenu::getId, m -> m, (a, b) -> a));
        Set<Long> withParents = new HashSet<>(allowed);
        for (Long id : allowed) {
            AdminMenu current = byId.get(id);
            while (current != null && current.getParentId() != null && current.getParentId() > 0) {
                withParents.add(current.getParentId());
                current = byId.get(current.getParentId());
            }
        }
        return withParents;
    }

    private List<AdminMenu> listEnabledMenus() {
        return adminMenuMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(AdminMenu::getStatus).eq(1)
                        .orderBy(AdminMenu::getSortOrder, true)
                        .orderBy(AdminMenu::getId, true));
    }

    private List<AdminMenu> listAllMenus() {
        return adminMenuMapper.selectListByQuery(
                QueryWrapper.create()
                        .orderBy(AdminMenu::getSortOrder, true)
                        .orderBy(AdminMenu::getId, true));
    }

    /** 判断 candidateId 是否位于 rootId 的子孙节点中 */
    private boolean isDescendant(Long rootId, Long candidateId) {
        List<AdminMenu> all = listAllMenus();
        Map<Long, Long> parentOf = all.stream()
                .collect(Collectors.toMap(AdminMenu::getId,
                        m -> m.getParentId() == null ? 0L : m.getParentId(),
                        (a, b) -> a));
        Long current = candidateId;
        int guard = 0;
        while (current != null && current > 0 && guard++ < 64) {
            if (Objects.equals(current, rootId)) {
                return true;
            }
            current = parentOf.get(current);
        }
        return false;
    }

    private List<AdminMenuTreeVO> buildTree(List<AdminMenu> menus) {
        Map<Long, AdminMenuTreeVO> map = new HashMap<>();
        for (AdminMenu menu : menus) {
            map.put(menu.getId(), toTreeVO(menu));
        }
        List<AdminMenuTreeVO> roots = new ArrayList<>();
        for (AdminMenu menu : menus) {
            AdminMenuTreeVO node = map.get(menu.getId());
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            if (parentId == 0L || !map.containsKey(parentId)) {
                roots.add(node);
            } else {
                map.get(parentId).getChildren().add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<AdminMenuTreeVO> nodes) {
        nodes.sort(Comparator.comparing(AdminMenuTreeVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (AdminMenuTreeVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }

    private AdminMenuTreeVO toTreeVO(AdminMenu menu) {
        return AdminMenuTreeVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .title(menu.getTitle())
                .path(menu.getPath())
                .component(menu.getComponent())
                .redirect(menu.getRedirect())
                .icon(menu.getIcon())
                .type(menu.getMenuType())
                .permission(menu.getPermissionCode())
                .sort(menu.getSortOrder())
                .visible(menu.getHidden() == null || menu.getHidden() == 0)
                .status(menu.getStatus())
                .children(new ArrayList<>())
                .build();
    }

    private AdminMenuVO toVO(AdminMenu menu) {
        return AdminMenuVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .title(menu.getTitle())
                .path(menu.getPath())
                .component(menu.getComponent())
                .redirect(menu.getRedirect())
                .icon(menu.getIcon())
                .menuType(menu.getMenuType())
                .permissionCode(menu.getPermissionCode())
                .sortOrder(menu.getSortOrder())
                .hidden(menu.getHidden())
                .status(menu.getStatus())
                .remark(menu.getRemark())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .build();
    }

    private AdminMenu requireMenu(Long id) {
        AdminMenu menu = adminMenuMapper.selectOneById(id);
        if (menu == null) {
            throw new CustomException(404, "菜单不存在");
        }
        return menu;
    }
}
