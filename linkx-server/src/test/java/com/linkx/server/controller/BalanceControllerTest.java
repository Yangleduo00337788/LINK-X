package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BalanceController 余额控制器集成测试
 */
@DisplayName("BalanceController 余额控制器集成测试")
class BalanceControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /balance 获取余额测试")
    class GetBalanceTests {

        @Test
        @DisplayName("获取余额应成功")
        void getBalance_success() throws Exception {
            TestUser user = registerAndLogin("balanceuser");

            mockMvc.perform(get("/balance")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("未登录获取余额应返回401")
        void getBalance_unauthorized() throws Exception {
            mockMvc.perform(get("/balance"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
