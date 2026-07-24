package com.linkx.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类：加载完整 Spring 上下文（MockMvc + H2 + 内嵌 Redis）。
 * <p>
 * 提供注册 / 登录辅助方法，返回可直接用于鉴权请求的访问令牌。
 * 所有需要真实接口调用的集成测试均应继承本类。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger(0);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        int port = EmbeddedRedis.startIfNeeded();
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> port);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 已登录测试用户上下文。 */
    protected static final class TestUser {
        public final long userId;
        public final String username;
        public final String accessToken;

        TestUser(long userId, String username, String accessToken) {
            this.userId = userId;
            this.username = username;
            this.accessToken = accessToken;
        }

        /** 返回 Authorization 头值。 */
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /**
     * 注册并登录一个全新用户，返回其令牌上下文。
     *
     * @param nicknamePrefix 昵称前缀，便于断言
     */
    protected TestUser registerAndLogin(String nicknamePrefix) {
        String username = "itu" + System.nanoTime() + USER_SEQ.incrementAndGet();
        // 用户名限制 4-32 位字母数字下划线，这里截断保证合法
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }
        String password = "Test1234abcd";
        register(username, password, nicknamePrefix + USER_SEQ.get());
        return login(username, password);
    }

    protected void register(String username, String password, String nickname) {
        try {
            String body = objectMapper.writeValueAsString(
                    new RegisterReq(username, password, nickname, username + "@linkx.test"));
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } catch (Exception e) {
            throw new IllegalStateException("注册测试用户失败: " + username, e);
        }
    }

    protected TestUser login(String username, String password) {
        return login(username, password, null);
    }

    protected TestUser login(String username, String password, String deviceId) {
        try {
            String body = objectMapper.writeValueAsString(new LoginReq(username, password));
            var builder = post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);
            if (deviceId != null && !deviceId.isBlank()) {
                builder = builder
                        .header("X-Device-Id", deviceId)
                        .header("X-Device-Name", "JUnit")
                        .header("X-Device-Type", "Test");
            }
            MvcResult result = mockMvc.perform(builder)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode data = root.get("data");
            String token = data.get("accessToken").asText();
            long userId = data.get("user").get("id").asLong();
            return new TestUser(userId, username, token);
        } catch (Exception e) {
            throw new IllegalStateException("登录测试用户失败: " + username, e);
        }
    }

    protected TestUser registerAndLoginWithDevice(String nicknamePrefix, String deviceId) {
        String username = "itu" + System.nanoTime() + USER_SEQ.incrementAndGet();
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }
        String password = "Test1234abcd";
        register(username, password, nicknamePrefix + USER_SEQ.get());
        return login(username, password, deviceId);
    }

    // ---- 简单请求体（避免依赖生产 DTO 的校验注解可见性） ----
    private record RegisterReq(String username, String password, String nickname, String email) {
    }

    private record LoginReq(String username, String password) {
    }

    @BeforeEach
    void baseSetup() {
        // 预留：子类可在此扩展公共准备逻辑
    }
}
