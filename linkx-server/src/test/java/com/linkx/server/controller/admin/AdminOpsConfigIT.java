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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端运营配置（推荐位 / 活动）")
class AdminOpsConfigIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";
    private static final String IMAGE = "https://cdn.example.com/linkx-ops-it.png";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("推荐位：创建 → 发布 → 客户端可见 → 下线后不可见")
    void recommendPublishLifecycle() throws Exception {
        TestUser admin = registerAndLogin("opsrecadm");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String title = "rec-it-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/admin/recommends")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotCode":"discover",
                                  "title":"%s",
                                  "subtitle":"sub",
                                  "imageUrl":"%s",
                                  "sortOrder":1
                                }
                                """.formatted(title, IMAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/app/recommends").param("slotCode", "discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")]").doesNotExist());

        mockMvc.perform(post("/admin/recommends/{id}/publish", id)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("published"));

        mockMvc.perform(get("/app/recommends").param("slotCode", "discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].title").value(title));

        mockMvc.perform(post("/admin/recommends/{id}/unpublish", id)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unpublished"));

        MvcResult after = mockMvc.perform(get("/app/recommends").param("slotCode", "discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode items = objectMapper.readTree(after.getResponse().getContentAsString()).path("data");
        for (JsonNode item : items) {
            if (item.path("id").asLong() == id) {
                throw new AssertionError("unpublished recommend still visible to client: " + id);
            }
        }
    }

    @Test
    @DisplayName("活动：创建 → 发布 → 客户端可见 → 下线后不可见")
    void activityPublishLifecycle() throws Exception {
        TestUser admin = registerAndLogin("opsactadm");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String title = "act-it-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/admin/activities")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"%s",
                                  "coverUrl":"%s",
                                  "description":"ops activity it",
                                  "sortOrder":2
                                }
                                """.formatted(title, IMAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/app/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/activities/{id}/publish", id)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("published"));

        mockMvc.perform(get("/app/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].title").value(title));

        mockMvc.perform(post("/admin/activities/{id}/unpublish", id)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult after = mockMvc.perform(get("/app/activities"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(after.getResponse().getContentAsString()).path("data");
        for (JsonNode item : items) {
            if (item.path("id").asLong() == id) {
                throw new AssertionError("unpublished activity still visible to client: " + id);
            }
        }
    }

    private void grantAdmin(long userId) {
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(userId)
                .roleId(ADMIN_ROLE)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(userId);
    }
}
