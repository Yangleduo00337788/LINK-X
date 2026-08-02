package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 健康检查端点测试（继承 BaseIntegrationTest 以启用内嵌 Redis）。
 */
class HealthControllerTest extends BaseIntegrationTest {

    @Test
    void healthEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.service").value("linkx-server"));
    }

    @Test
    void livenessEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void readinessEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void healthShouldIncludeServiceStatusAndTimestamp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").exists());
    }
}
