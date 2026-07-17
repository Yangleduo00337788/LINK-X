package com.linkx.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 健康检查端点测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.service").value("linkx-server"));
    }

    @Test
    void livenessEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void readinessEndpointShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void healthShouldIncludeMysqlAndRedisStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mysql").exists())
                .andExpect(jsonPath("$.data.redis").exists())
                .andExpect(jsonPath("$.data.mysql.status").exists())
                .andExpect(jsonPath("$.data.redis.status").exists());
    }
}
