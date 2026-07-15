package com.linkx.server.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 基础设施冒烟测试：验证 H2 + 内嵌 Redis + MockMvc + 认证链路整体可用。
 * 若此测试通过，说明后续各业务模块集成测试的运行环境已就绪。
 */
class InfraSmokeTest extends BaseIntegrationTest {

    @Test
    @DisplayName("上下文加载 + 注册登录 + 鉴权访问已有接口")
    void contextLoadsAndAuthWorks() throws Exception {
        TestUser user = registerAndLogin("smoke");

        // 已登录：/user/me 返回当前用户
        mockMvc.perform(get("/user/me").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());

        // 已登录：好友列表初始为空数组
        mockMvc.perform(get("/friend/list").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口应被拦截")
    void unauthorizedRejected() throws Exception {
        mockMvc.perform(get("/friend/list"))
                .andExpect(status().isUnauthorized());
    }
}
