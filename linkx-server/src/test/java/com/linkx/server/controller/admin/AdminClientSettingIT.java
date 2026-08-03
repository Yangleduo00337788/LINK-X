package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端客户端配置（客服联系 / 敏感词总开关）")
class AdminClientSettingIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;
    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;
    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Test
    @DisplayName("统一 PUT /admin/settings 可更新客户端配置")
    void unifiedPut_clientSection() throws Exception {
        TestUser admin = registerAndLogin("cssettings");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        JsonNode client = readClientSettings(admin);
        String email = "unified-" + System.nanoTime() + "@linkx.test";
        try {
            String body = """
                    {
                      "client": {
                        "captchaEnabled":%s,
                        "appVersion":"%s",
                        "appChannel":"%s",
                        "releaseNotes":%s,
                        "downloadUrl":%s,
                        "forceUpdate":%s,
                        "minSupportedVersion":"%s",
                        "maxUploadBytes":%d,
                        "sensitiveFilterEnabled":%s,
                        "supportEmail":"%s",
                        "supportPhone":"%s",
                        "feedbackSlaHours":%d
                      }
                    }
                    """.formatted(
                    client.path("captchaEnabled").asBoolean(false),
                    client.path("appVersion").asText("1.0.0"),
                    client.path("appChannel").asText("stable"),
                    jsonString(client.path("releaseNotes").asText("")),
                    jsonString(client.path("downloadUrl").asText("")),
                    client.path("forceUpdate").asBoolean(false),
                    client.path("minSupportedVersion").asText(""),
                    client.path("maxUploadBytes").asLong(20L * 1024 * 1024),
                    client.path("sensitiveFilterEnabled").asBoolean(true),
                    email,
                    client.path("supportPhone").asText(""),
                    client.path("feedbackSlaHours").asInt(24)
            );
            mockMvc.perform(put("/admin/settings")
                            .header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.client.supportEmail").value(email));
        } finally {
            putClient(admin, client,
                    client.path("supportEmail").asText(""),
                    client.path("supportPhone").asText(""),
                    client.path("sensitiveFilterEnabled").asBoolean(true));
        }
    }

    @Test
    @DisplayName("更新客服邮箱/电话后公开版本接口可读取")
    void supportContactExposedOnAppVersion() throws Exception {
        TestUser admin = registerAndLogin("cssupport");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        JsonNode client = readClientSettings(admin);
        String email = "support-it-" + System.nanoTime() + "@linkx.test";
        String phone = "400-800-1234";
        try {
            putClient(admin, client, email, phone,
                    client.path("sensitiveFilterEnabled").asBoolean(true));

            mockMvc.perform(get("/app/version").param("current", "1.0.0").param("channel", "stable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.supportEmail").value(email))
                    .andExpect(jsonPath("$.data.supportPhone").value(phone));

            mockMvc.perform(get("/admin/settings").header("Authorization", admin.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.client.supportEmail").value(email))
                    .andExpect(jsonPath("$.data.client.supportPhone").value(phone));
        } finally {
            putClient(admin, client,
                    client.path("supportEmail").asText(""),
                    client.path("supportPhone").asText(""),
                    client.path("sensitiveFilterEnabled").asBoolean(true));
        }
    }

    @Test
    @DisplayName("关闭敏感词总开关后 containsSensitive 短路为 false")
    void sensitiveFilterMasterSwitch() throws Exception {
        TestUser admin = registerAndLogin("cssens");
        grantAdmin(admin.userId);
        admin = login(admin.username, PASSWORD);

        String word = "lxbad" + System.nanoTime();
        mockMvc.perform(post("/admin/sensitive-words")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "word":"%s",
                                  "category":"general",
                                  "action":"filter",
                                  "replacement":"***",
                                  "enabled":true
                                }
                                """.formatted(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        sensitiveWordService.refreshDictionary();
        assertTrue(sensitiveWordService.containsSensitive("hello " + word + " world"));

        JsonNode client = readClientSettings(admin);
        putClient(admin, client,
                client.path("supportEmail").asText(""),
                client.path("supportPhone").asText(""),
                false);

        assertFalse(sensitiveWordService.containsSensitive("hello " + word + " world"));

        // 恢复总开关，避免污染后续用例
        putClient(admin, client,
                client.path("supportEmail").asText(""),
                client.path("supportPhone").asText(""),
                true);
        assertTrue(sensitiveWordService.containsSensitive("hello " + word + " world"));
    }

    private JsonNode readClientSettings(TestUser admin) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/settings").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("client");
    }

    private void putClient(TestUser admin, JsonNode client, String email, String phone, boolean sensitiveEnabled)
            throws Exception {
        String body = """
                {
                  "captchaEnabled":%s,
                  "appVersion":"%s",
                  "appChannel":"%s",
                  "releaseNotes":%s,
                  "downloadUrl":%s,
                  "forceUpdate":%s,
                  "minSupportedVersion":"%s",
                  "maxUploadBytes":%d,
                  "sensitiveFilterEnabled":%s,
                  "supportEmail":"%s",
                  "supportPhone":"%s",
                  "feedbackSlaHours":%d
                }
                """.formatted(
                client.path("captchaEnabled").asBoolean(false),
                client.path("appVersion").asText("1.0.0"),
                client.path("appChannel").asText("stable"),
                jsonString(client.path("releaseNotes").asText("")),
                jsonString(client.path("downloadUrl").asText("")),
                client.path("forceUpdate").asBoolean(false),
                client.path("minSupportedVersion").asText(""),
                client.path("maxUploadBytes").asLong(20L * 1024 * 1024),
                sensitiveEnabled,
                email == null ? "" : email,
                phone == null ? "" : phone,
                client.path("feedbackSlaHours").asInt(24)
        );
        mockMvc.perform(put("/admin/settings/client")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private static String jsonString(String value) {
        if (value == null || value.isEmpty()) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
