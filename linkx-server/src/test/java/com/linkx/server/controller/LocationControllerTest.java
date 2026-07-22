package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("LocationController 位置控制器集成测试")
class LocationControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /location/search")
    class SearchTests {

        @Test
        @DisplayName("空关键词应返回空数组且不访问外部服务")
        void search_blankKeyword() throws Exception {
            TestUser user = registerAndLogin("locuser");

            mockMvc.perform(get("/location/search")
                            .param("q", "   ")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("未登录应返回401")
        void search_unauthorized() throws Exception {
            mockMvc.perform(get("/location/search").param("q", "beijing"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
