package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RedPacketController 红包控制器集成测试
 */
@DisplayName("RedPacketController 红包控制器集成测试")
class RedPacketControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /red-packet 发送红包测试")
    class SendRedPacketTests {

        @Test
        @DisplayName("未登录发送红包应返回401")
        void sendRedPacket_unauthorized() throws Exception {
            String body = """
                {
                    "conversationId": 1,
                    "type": "normal",
                    "amount": "10.00",
                    "count": 1
                }
                """;

            mockMvc.perform(post("/red-packet")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
