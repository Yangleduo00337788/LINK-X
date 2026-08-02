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

@DisplayName("管理端版本管理 CRUD + 发布")
class AdminVersionIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("创建草稿、发布、客户端检查更新可见")
    void versionCrudAndPublish() throws Exception {
        TestUser admin = registerAndLogin("veradmin");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String version = "9.9." + (System.nanoTime() % 1000);
        MvcResult createRes = mockMvc.perform(post("/admin/versions")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":"%s",
                                  "channel":"stable",
                                  "releaseNotes":"integration test",
                                  "downloadUrl":"https://example.com/app.apk",
                                  "forceUpdate":false,
                                  "minSupportedVersion":"1.0.0"
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        String id = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get("/admin/versions/" + id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(version));

        mockMvc.perform(post("/admin/versions/" + id + "/publish")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"));

        mockMvc.perform(get("/app/version").param("current", "1.0.0").param("channel", "stable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(version))
                .andExpect(jsonPath("$.data.hasUpdate").value(true));

        mockMvc.perform(put("/admin/versions/" + id)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":"%s",
                                  "channel":"stable",
                                  "releaseNotes":"x",
                                  "downloadUrl":"https://example.com/app.apk",
                                  "forceUpdate":false,
                                  "minSupportedVersion":""
                                }
                                """.formatted(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(delete("/admin/versions/" + id).header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("版本列表可查询")
    void listVersions() throws Exception {
        TestUser admin = registerAndLogin("verlist");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        MvcResult res = mockMvc.perform(get("/admin/versions")
                        .header("Authorization", admin.bearer())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        assertTrue(data.path("total").asLong() >= 0);
    }

    private void grantAdmin(long userId) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(ADMIN_ROLE);
        ur.setDeleted(0);
        sysUserRoleMapper.insert(ur);
        rbacService.evictUserCache(userId);
    }
}
