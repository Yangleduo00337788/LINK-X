package com.linkx.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 完整复现"随便输入验证码都通过"的 bug 路径。
 * 走完整 HTTP，请求 → controller → service → Redis → 拦截。
 */
class ResetEmailCodeVerifyTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void codeMustBeValidated_evenBeforeUserLookup() throws Exception {
        String username = "ut" + System.nanoTime() % 1_000_000_000L;
        String correctCode = "482915";

        // 场景 A：从未发送验证码，发送一个 POST 进来 → 应该 400
        String bodyNoCode = """
                {"username":"%s","code":"000000","newPassword":"NewPass@1234"}
                """.formatted(username);

        MvcResult r1 = mockMvc.perform(post("/auth/reset-password-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyNoCode))
                .andReturn();
        JsonNode resp1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        System.out.println(">>> [从未发送] http=" + r1.getResponse().getStatus() + ", body=" + resp1.toString());
        assertEquals(400, resp1.get("code").asInt(), "未发送验证码应被拦截");

        // 场景 B：Redis 写入正确验证码，再用错误验证码 → 应该 400
        redis.opsForValue().set("linkx:reset-email:" + username, correctCode);

        String bodyWrongCode = """
                {"username":"%s","code":"111111","newPassword":"NewPass@1234"}
                """.formatted(username);
        MvcResult r2 = mockMvc.perform(post("/auth/reset-password-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWrongCode))
                .andReturn();
        JsonNode resp2 = objectMapper.readTree(r2.getResponse().getContentAsString());
        System.out.println(">>> [错误验证码] http=" + r2.getResponse().getStatus() + ", body=" + resp2.toString());
        assertEquals(400, resp2.get("code").asInt(), "错误验证码应被拦截");

        String remaining = redis.opsForValue().get("linkx:reset-email:" + username);
        System.out.println(">>> 错误验证码后 Redis 残留=" + remaining);
        assertNotNull(remaining, "错误一次不应删除 key（避免用户手抖一次后正确验证码被吞掉）");

        // 场景 C：同一个用户用大小写不同的 code 是否通过（应通过，因为 equalsIgnoreCase）
        redis.opsForValue().set("linkx:reset-email:" + username, correctCode);
        String bodyLower = """
                {"username":"%s","code":"482915","newPassword":"NewPass@1234"}
                """.formatted(username);
        System.out.println(">>> [完全匹配验证码] 提交后用户不存在会抛错，但校验通过，应该走到 user 查询阶段");
        MvcResult r3 = mockMvc.perform(post("/auth/reset-password-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLower))
                .andReturn();
        JsonNode resp3 = objectMapper.readTree(r3.getResponse().getContentAsString());
        System.out.println(">>> [正确验证码,用户不存在] http=" + r3.getResponse().getStatus() + ", body=" + resp3.toString());
    }

    @Test
    void fullFlow_sendCode_thenVerify_correctCodeSucceeds() throws Exception {
        // 注册真实用户（含邮箱验证码），确保 send-reset-code 能落 Redis
        String username = "rt" + System.nanoTime() % 1_000_000_000L;
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }
        String password = "Test1234abcd";
        register(username, password, "重置测试");

        MvcResult sr = mockMvc.perform(post("/auth/send-reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s"}
                                """.formatted(username)))
                .andReturn();
        assertEquals(200, objectMapper.readTree(sr.getResponse().getContentAsString()).get("code").asInt());

        String stored = redis.opsForValue().get("linkx:reset-email:" + username);
        assertNotNull(stored, "发送后 Redis 必须存有验证码");
        assertEquals(6, stored.length());

        MvcResult wrong = mockMvc.perform(post("/auth/reset-password-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","code":"000000","newPassword":"Test1234abcd"}
                                """.formatted(username)))
                .andReturn();
        JsonNode wrongBody = objectMapper.readTree(wrong.getResponse().getContentAsString());
        assertEquals(400, wrongBody.get("code").asInt());

        String stillThere = redis.opsForValue().get("linkx:reset-email:" + username);
        assertNotNull(stillThere, "错误一次不应该删除 key，验证码应该还在（避免用户手抖一次后正确码被吞）");

        String bodyCorrect = """
                {"username":"%s","code":"%s","newPassword":"Test1234abcd"}
                """.formatted(username, stored);
        MvcResult ok = mockMvc.perform(post("/auth/reset-password-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCorrect))
                .andReturn();
        JsonNode okBody = objectMapper.readTree(ok.getResponse().getContentAsString());
        assertEquals(200, okBody.get("code").asInt(), "正确验证码应成功重置密码: " + okBody);
    }
}