package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import com.linkx.server.util.ApiEncryptUtils;
import com.linkx.server.util.ApiSignUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端 API 签名 + 加密")
@TestPropertySource(properties = {
        "linkx.security.api-sign-enabled=true",
        "linkx.security.api-encrypt-enabled=true"
})
class AdminApiSecurityEncryptIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;

    @Test
    @DisplayName("登录后 GET 响应加密、PUT 请求加密、step-up 明文")
    void signedEncryptedAdminApi() throws Exception {
        TestUser admin = registerAndLogin("encadm");
        grantAdmin(admin.userId);
        AdminSession session = adminLoginWithSignKey(admin.username, "Test1234abcd");

        MvcResult me = mockMvc.perform(get("/admin/auth/me")
                        .header("Authorization", session.bearer())
                        .headers(session.secureGetHeaders("/admin/auth/me")))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString())
                .andReturn();

        JsonNode meRoot = objectMapper.readTree(me.getResponse().getContentAsString());
        String encryptedData = meRoot.path("data").asText();
        String plainJson = ApiEncryptUtils.decryptUtf8FromBase64(session.signKeyBytes(), encryptedData);
        JsonNode profile = objectMapper.readTree(plainJson);
        assertEquals(admin.username, profile.path("username").asText());

        mockMvc.perform(get("/admin/settings")
                        .header("Authorization", session.bearer())
                        .headers(session.secureGetHeaders("/admin/settings")))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString());

        String plainBody = "{\"method\":\"totp\",\"code\":\"000000\",\"action\":\"test\"}";
        String encryptedBody = "\"" + ApiEncryptUtils.encryptUtf8ToBase64(session.signKeyBytes(), plainBody) + "\"";
        mockMvc.perform(post("/admin/auth/step-up/verify")
                        .header("Authorization", session.bearer())
                        .header(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1")
                        .headers(session.securePostHeaders(
                                "/admin/auth/step-up/verify",
                                encryptedBody,
                                session.encryptedEmptyQuery()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(encryptedBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private AdminSession adminLoginWithSignKey(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apiSignKey").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        String signKeyHex = root.path("data").path("apiSignKey").asText();
        assertNotNull(token);
        assertTrue(signKeyHex.length() >= 64);
        return new AdminSession(token, ApiSignUtils.hexToBytes(signKeyHex));
    }

    private void grantAdmin(long userId) {
        sysUserRoleMapper.insert(SysUserRole.builder().userId(userId).roleId(ADMIN_ROLE).build());
        rbacService.evictUserCache(userId);
    }

    private static final class AdminSession {
        private final String token;
        private final byte[] signKey;

        private AdminSession(String token, byte[] signKey) {
            this.token = token;
            this.signKey = signKey;
        }

        String bearer() {
            return "Bearer " + token;
        }

        byte[] signKeyBytes() {
            return signKey;
        }

        org.springframework.http.HttpHeaders secureGetHeaders(String path) throws Exception {
            String encryptedQuery = encryptedEmptyQuery();
            return secureHeaders("GET", path, "", encryptedQuery);
        }

        org.springframework.http.HttpHeaders securePostHeaders(String path, String body, String encryptedQuery)
                throws Exception {
            return secureHeaders("POST", path, body, encryptedQuery);
        }

        String encryptedEmptyQuery() {
            return "\"" + ApiEncryptUtils.encryptUtf8ToBase64(signKey, "{}") + "\"";
        }

        org.springframework.http.HttpHeaders secureHeaders(
                String method, String path, String body, String encryptedQuery) throws Exception {
            org.springframework.http.HttpHeaders headers = signHeaders(method, path, body, encryptedQuery);
            headers.set(ApiEncryptUtils.HEADER_CONTENT_ENCRYPTED, "1");
            headers.set(ApiEncryptUtils.HEADER_ENCRYPTED_QUERY, encryptedQuery);
            return headers;
        }

        org.springframework.http.HttpHeaders signHeaders(
                String method, String path, String body, String encryptedQuery) throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String bodyHash = ApiSignUtils.sha256Hex(body == null ? "" : body);
            String queryHash = ApiSignUtils.queryHashHex(encryptedQuery == null ? "" : encryptedQuery);
            String payload = ApiSignUtils.buildPayload(timestamp, nonce, method, path, bodyHash, queryHash);
            String signature = ApiSignUtils.signHex(signKey, payload);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(ApiSignUtils.HEADER_TIMESTAMP, timestamp);
            headers.set(ApiSignUtils.HEADER_NONCE, nonce);
            headers.set(ApiSignUtils.HEADER_SIGNATURE, signature);
            return headers;
        }
    }
}
