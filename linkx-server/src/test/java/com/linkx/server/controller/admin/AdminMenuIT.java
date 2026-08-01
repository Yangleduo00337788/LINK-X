package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端菜单 CRUD / 排序")
class AdminMenuIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE_ID = 1001L;
    private static final long ROLE_OPS = 1003L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("超管可新增/编辑/排序/删除菜单；运营无写权限")
    void adminCanMutateMenusOpsCannot() throws Exception {
        TestUser admin = registerAndLogin("mnadm");
        grantRole(admin.userId, ADMIN_ROLE_ID);
        admin = login(admin.username, "Test1234abcd");

        String name = "smoke_menu_" + System.currentTimeMillis();
        String createBody = """
                {
                  "parentId": 0,
                  "name": "%s",
                  "title": "冒烟菜单",
                  "path": "/admin/smoke-menu",
                  "component": "views/DashboardView",
                  "icon": "Menu",
                  "menuType": "menu",
                  "permissionCode": "admin:dashboard:view",
                  "sortOrder": 99,
                  "hidden": 0,
                  "status": 1
                }
                """.formatted(name);

        MvcResult created = mockMvc.perform(post("/admin/menus")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long menuId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").asLong();
        assertTrue(menuId > 0);

        mockMvc.perform(put("/admin/menus/{id}", menuId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 0,
                                  "name": "%s",
                                  "title": "冒烟菜单已改",
                                  "path": "/admin/smoke-menu",
                                  "component": "views/DashboardView",
                                  "icon": "Menu",
                                  "menuType": "menu",
                                  "permissionCode": "admin:dashboard:view",
                                  "sortOrder": 1,
                                  "hidden": 0,
                                  "status": 1
                                }
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/menus/reorder")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"id": %d, "parentId": 0, "sortOrder": 50}
                                  ]
                                }
                                """.formatted(menuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult detail = mockMvc.perform(get("/admin/menus/{id}", menuId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(detail.getResponse().getContentAsString()).path("data");
        assertEquals("冒烟菜单已改", data.path("title").asText());
        assertEquals(50, data.path("sortOrder").asInt());

        mockMvc.perform(delete("/admin/menus/{id}", menuId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        TestUser ops = registerAndLogin("mnops");
        grantRole(ops.userId, ROLE_OPS);
        ops = login(ops.username, "Test1234abcd");

        mockMvc.perform(post("/admin/menus")
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace(name, name + "_ops")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/menus/reorder")
                        .header("Authorization", ops.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":1,\"sortOrder\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void grantRole(long userId, long roleId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
