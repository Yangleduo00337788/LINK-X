package com.linkx.server.controller.admin;

import com.linkx.server.entity.SysUserRole;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理端高危操作二次验证")
@TestPropertySource(properties = "linkx.auth.admin-step-up-enabled=true")
class AdminStepUpIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("封禁无 step-up 返回 428，邮箱验证后可成功")
    void banRequiresStepUpThenSucceedsWithEmail() throws Exception {
        TestUser admin = registerAndLogin("stupadm");
        grantAdmin(admin.userId);
        admin = login(admin.username, "Test1234abcd");

        TestUser victim = registerAndLogin("stupvct");

        mockMvc.perform(post("/admin/users/{id}/ban", victim.userId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(428))
                .andExpect(jsonPath("$.data.methods").isArray());

        mockMvc.perform(get("/admin/auth/step-up/options")
                        .param("action", "admin:user:ban")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.emailBound").value(true));

        mockMvc.perform(post("/admin/auth/step-up/request")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"action\":\"admin:user:ban\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String raw = stringRedisTemplate.opsForValue().get("linkx:admin:stepup:email:" + admin.userId);
        assertNotNull(raw);
        String code = raw.split("\\|", 2)[0];
        assertTrue(code.matches("\\d{6}"));

        MvcResult verify = mockMvc.perform(post("/admin/auth/step-up/verify")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"code\":\"" + code + "\",\"action\":\"admin:user:ban\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stepUpToken").isNotEmpty())
                .andReturn();

        String stepUpToken = objectMapper.readTree(verify.getResponse().getContentAsString())
                .path("data").path("stepUpToken").asText();

        mockMvc.perform(post("/admin/users/{id}/ban", victim.userId)
                        .header("Authorization", admin.bearer())
                        .header("X-Step-Up-Token", stepUpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("错误的 step-up token 仍要求二次验证")
    void invalidTokenRejected() throws Exception {
        TestUser admin = registerAndLogin("stupbad");
        grantAdmin(admin.userId);
        admin = login(admin.username, "Test1234abcd");
        TestUser victim = registerAndLogin("stupbad2");

        mockMvc.perform(post("/admin/users/{id}/freeze", victim.userId)
                        .header("Authorization", admin.bearer())
                        .header("X-Step-Up-Token", "not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(428));
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
