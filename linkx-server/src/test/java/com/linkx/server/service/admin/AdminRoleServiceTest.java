package com.linkx.server.service.admin;

import com.linkx.server.common.admin.DataScopeType;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPermissionDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignMenuDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignPermissionDTO;
import com.linkx.server.controller.admin.dto.AdminRoleAssignUserDTO;
import com.linkx.server.controller.admin.dto.AdminRoleDTO;
import com.linkx.server.controller.admin.vo.AdminRoleUserVO;
import com.linkx.server.controller.admin.vo.AdminRoleVO;
import com.linkx.server.entity.SysPermission;
import com.linkx.server.entity.SysRole;
import com.linkx.server.entity.SysRolePermission;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.AdminRoleMenu;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysDeptMapper;
import com.linkx.server.mapper.SysPermissionMapper;
import com.linkx.server.mapper.SysRoleDeptMapper;
import com.linkx.server.mapper.SysRoleMapper;
import com.linkx.server.mapper.SysRolePermissionMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.AdminRoleMenuMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.impl.AdminRoleServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRoleService 角色/权限")
class AdminRoleServiceTest {

    @Mock SysRoleMapper sysRoleMapper;
    @Mock SysPermissionMapper sysPermissionMapper;
    @Mock SysRolePermissionMapper sysRolePermissionMapper;
    @Mock AdminRoleMenuMapper adminRoleMenuMapper;
    @Mock SysUserRoleMapper sysUserRoleMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysDeptMapper sysDeptMapper;
    @Mock SysRoleDeptMapper sysRoleDeptMapper;
    @Mock RbacService rbacService;

    private AdminRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminRoleServiceImpl(
                sysRoleMapper, sysPermissionMapper, sysRolePermissionMapper, adminRoleMenuMapper,
                sysUserRoleMapper, sysUserMapper, sysDeptMapper, sysRoleDeptMapper, rbacService);
    }

    private SysRole role(Long id, String code) {
        return SysRole.builder()
                .id(id)
                .roleCode(code)
                .roleName(code + "-name")
                .status(1)
                .dataScope(DataScopeType.ALL)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    @Test
    @DisplayName("角色列表与详情")
    void list_and_detail() {
        when(sysRoleMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        when(sysRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(role(1L, "ops")));
        when(sysRoleDeptMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AdminPageQueryDTO q = new AdminPageQueryDTO();
        q.setPage(1);
        q.setSize(20);
        q.setKeyword("ops");
        q.setStatus(1);
        var page = service.list(q);
        assertEquals(1, page.getTotal());
        assertEquals("ops", page.getItems().get(0).getRoleCode());

        when(sysRoleMapper.selectOneById(1L)).thenReturn(role(1L, "ops"));
        AdminRoleVO detail = service.detail(1L);
        assertEquals(1L, detail.getId());
    }

    @Test
    @DisplayName("创建角色并拒绝内置编码")
    void create_role() {
        assertThrows(CustomException.class, () -> {
            AdminRoleDTO bad = new AdminRoleDTO();
            bad.setRoleCode("admin");
            bad.setRoleName("Admin");
            service.create(bad, 1L);
        });

        SysRole created = role(10L, "custom_ops");
        when(rbacService.createRole(eq("custom_ops"), eq("Custom"), any(), eq(1L))).thenReturn(created);
        when(sysRoleDeptMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        AdminRoleDTO dto = new AdminRoleDTO();
        dto.setRoleCode("custom_ops");
        dto.setRoleName("Custom");
        dto.setDescription("d");
        dto.setStatus(0);
        dto.setDataScope(DataScopeType.ALL);
        Long id = service.create(dto, 1L);
        assertEquals(10L, id);
        verify(sysRoleMapper).update(created);
    }

    @Test
    @DisplayName("更新/删除角色守卫")
    void update_delete() {
        SysRole custom = role(2L, "editor");
        when(sysRoleMapper.selectOneById(2L)).thenReturn(custom);
        when(sysRoleDeptMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        AdminRoleDTO dto = new AdminRoleDTO();
        dto.setRoleName("Editor2");
        dto.setDescription("x");
        dto.setStatus(1);
        dto.setDataScope(DataScopeType.SELF);
        service.update(2L, dto, 9L);
        verify(sysRoleMapper).update(custom);

        service.delete(2L);
        verify(sysRoleMapper).deleteById(2L);

        when(sysRoleMapper.selectOneById(3L)).thenReturn(role(3L, "super_admin"));
        assertThrows(CustomException.class, () -> service.delete(3L));
    }

    @Test
    @DisplayName("菜单分配与角色用户")
    void menus_and_users() {
        when(sysRoleMapper.selectOneById(5L)).thenReturn(role(5L, "ops"));
        when(adminRoleMenuMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                AdminRoleMenu.builder().roleId(5L).menuId(100L).build()
        ));
        assertEquals(List.of(100L), service.getRoleMenuIds(5L));

        AdminRoleAssignMenuDTO menuDto = new AdminRoleAssignMenuDTO();
        menuDto.setMenuIds(new ArrayList<>(Arrays.asList(1L, 2L, null)));
        service.assignMenus(5L, menuDto);
        verify(adminRoleMenuMapper, times(2)).insert(any(AdminRoleMenu.class));

        when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUserRole.builder().userId(7L).roleId(5L).build()
        ));
        when(sysUserMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUser.builder().id(7L).username("u7").nickname("N7").status(1).build()
        ));
        List<AdminRoleUserVO> users = service.listRoleUsers(5L);
        assertEquals(1, users.size());
        assertEquals("u7", users.get(0).getUsername());
    }

    @Test
    @DisplayName("分配用户与权限 CRUD")
    void assignUsers_and_permissions() {
        when(sysRoleMapper.selectOneById(6L)).thenReturn(role(6L, "ops"));
        when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(sysUserMapper.selectOneById(20L)).thenReturn(SysUser.builder().id(20L).username("a").build());

        AdminRoleAssignUserDTO udto = new AdminRoleAssignUserDTO();
        udto.setUserIds(List.of(20L));
        service.assignUsers(6L, udto, 1L);
        verify(sysUserRoleMapper).insert(any(SysUserRole.class));
        verify(rbacService).evictUserCache(20L);

        when(sysRoleMapper.selectOneById(7L)).thenReturn(role(7L, "user"));
        assertThrows(CustomException.class, () -> service.assignUsers(7L, udto, 1L));

        when(sysPermissionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(sysPermissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysPermission.builder().id(1L).permissionCode("p.a").permissionName("A").status(1).build()
        ));
        AdminPageQueryDTO pq = new AdminPageQueryDTO();
        pq.setKeyword("p");
        assertEquals(1, service.listPermissions(pq).getItems().size());

        AdminPermissionDTO pdto = new AdminPermissionDTO();
        pdto.setPermissionCode("p.new");
        pdto.setPermissionName("New");
        when(sysPermissionMapper.insert(any(SysPermission.class))).thenAnswer(inv -> {
            ((SysPermission) inv.getArgument(0)).setId(88L);
            return 1;
        });
        assertEquals(88L, service.createPermission(pdto));

        when(sysPermissionMapper.selectOneById(88L)).thenReturn(
                SysPermission.builder().id(88L).permissionCode("p.new").permissionName("New").status(1).build());
        pdto.setPermissionName("New2");
        service.updatePermission(88L, pdto);
        verify(sysPermissionMapper).update(any(SysPermission.class));
        service.deletePermission(88L);
        verify(sysPermissionMapper).deleteById(88L);
    }

    @Test
    @DisplayName("分配权限与角色权限列表")
    void assignPermissions() {
        when(sysRoleMapper.selectOneById(8L)).thenReturn(role(8L, "ops"));
        when(sysPermissionMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(2L);
        when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUserRole.builder().userId(30L).roleId(8L).build()
        ));

        AdminRoleAssignPermissionDTO dto = new AdminRoleAssignPermissionDTO();
        dto.setPermissionIds(List.of(1L, 2L));
        service.assignPermissions(8L, dto);
        verify(sysRolePermissionMapper, times(2)).insert(any(SysRolePermission.class));
        verify(rbacService).evictUserCache(30L);

        when(sysRolePermissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysRolePermission.builder().roleId(8L).permissionId(1L).build()
        ));
        assertEquals(List.of(1L), service.getRolePermissionIds(8L));
    }

    @Test
    @DisplayName("创建角色非法 dataScope")
    void createInvalidDataScope() {
        SysRole created = role(11L, "scope_bad");
        when(rbacService.createRole(anyString(), anyString(), any(), anyLong())).thenReturn(created);
        AdminRoleDTO dto = new AdminRoleDTO();
        dto.setRoleCode("scope_bad");
        dto.setRoleName("Bad");
        dto.setDataScope(999);
        assertThrows(CustomException.class, () -> service.create(dto, 1L));
    }

    @Test
    @DisplayName("super_admin 不能清空用户")
    void superAdminKeepOneUser() {
        when(sysRoleMapper.selectOneById(12L)).thenReturn(role(12L, "super_admin"));
        when(sysUserRoleMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                SysUserRole.builder().userId(1L).roleId(12L).build()
        ));
        AdminRoleAssignUserDTO dto = new AdminRoleAssignUserDTO();
        dto.setUserIds(List.of());
        assertThrows(CustomException.class, () -> service.assignUsers(12L, dto, 1L));
    }

    @Test
    @DisplayName("角色不存在")
    void detailMissing() {
        when(sysRoleMapper.selectOneById(404L)).thenReturn(null);
        assertThrows(CustomException.class, () -> service.detail(404L));
    }
}
