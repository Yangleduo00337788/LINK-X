package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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

    @Nested
    @DisplayName("GET /balance/logs 流水测试")
    class ListLogsTests {

        @Test
        @DisplayName("查询流水应成功")
        void listLogs_success() throws Exception {
            TestUser user = registerAndLogin("balancelogs");

            mockMvc.perform(get("/balance/logs")
                            .param("limit", "10")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("POST /balance/recharge 充值测试")
    class RechargeTests {

        @Test
        @DisplayName("充值应成功并增加余额")
        void recharge_success() throws Exception {
            TestUser user = registerAndLogin("rechargeuser");

            mockMvc.perform(post("/balance/recharge")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":12.50}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.balance").value(12.50));

            mockMvc.perform(get("/balance/logs")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].type").value("recharge"));
        }

        @Test
        @DisplayName("非法金额应失败")
        void recharge_invalidAmount() throws Exception {
            TestUser user = registerAndLogin("rechargebad");

            mockMvc.perform(post("/balance/recharge")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":0}"))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
