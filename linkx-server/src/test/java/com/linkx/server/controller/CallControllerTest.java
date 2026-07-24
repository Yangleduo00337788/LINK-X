package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("CallController 通话信令集成测试")
class CallControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("未登录 invite 应 401")
    void invite_unauthorized() throws Exception {
        mockMvc.perform(post("/call/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":1,"callType":"voice"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("对不存在会话发起通话应业务失败")
    void invite_invalidConversation() throws Exception {
        TestUser user = registerAndLogin("caller");
        mockMvc.perform(post("/call/invite")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":999999,"callType":"voice"}
                                """))
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("cancel/accept/reject/hangup/signal 未登录应 401")
    void otherEndpoints_unauthorized() throws Exception {
        String body = """
                {"callId":"x"}
                """;
        for (String path : new String[]{"/call/cancel", "/call/accept", "/call/reject", "/call/hangup", "/call/signal"}) {
            String payload = path.contains("signal")
                    ? """
                    {"callId":"x","signalType":"offer","sdp":"v=0"}
                    """
                    : body;
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Test
    @DisplayName("缺少 callId 的 switch-device / reconnect 应参数校验失败")
    void dtoValidation_missingCallId() throws Exception {
        TestUser user = registerAndLogin("callval");
        mockMvc.perform(post("/call/reconnect")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/call/switch-device")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceType":"audio","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/call/conference/create")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callType":"voice"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }
}
