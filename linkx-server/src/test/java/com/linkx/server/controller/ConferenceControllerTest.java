package com.linkx.server.controller;

import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.service.ChatService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("ConferenceController 会议控制器集成测试")
class ConferenceControllerTest extends BaseIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Nested
    @DisplayName("会议生命周期")
    class ConferenceLifecycleTests {

        @Test
        @DisplayName("创建、加入、查询参与者、离开、结束会议")
        void createJoinListLeaveEnd_success() throws Exception {
            TestUser host = registerAndLogin("confhost");
            TestUser guest = registerAndLogin("confguest");
            ensureFriends(host, guest);
            ConversationVO conv = chatService.getOrCreatePrivateConversation(host.userId, guest.userId);

            String createBody = """
                    {"conversationId":%d,"title":"集成测试会议","type":"video","scene":"meeting"}
                    """.formatted(conv.getId());
            MvcResult createResult = mockMvc.perform(post("/conference/create")
                            .header("Authorization", host.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.conversationId").value(conv.getId()))
                    .andReturn();
            long conferenceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .path("data").path("id").asLong();

            mockMvc.perform(post("/conference/join")
                            .header("Authorization", guest.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"conferenceId":%d}
                                    """.formatted(conferenceId)))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/conference/info/{id}", conferenceId)
                            .header("Authorization", host.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.participants").isArray())
                    .andExpect(jsonPath("$.data.participants.length()").value(greaterThanOrEqualTo(2)));

            mockMvc.perform(get("/conference/active")
                            .header("Authorization", host.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(conferenceId)).exists());

            mockMvc.perform(get("/conference/active-in-conversation")
                            .param("conversationId", String.valueOf(conv.getId()))
                            .header("Authorization", host.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(conferenceId));

            mockMvc.perform(post("/conference/leave")
                            .header("Authorization", guest.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"conferenceId":%d}
                                    """.formatted(conferenceId)))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(post("/conference/end")
                            .header("Authorization", host.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"conferenceId":%d}
                                    """.formatted(conferenceId)))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/conference/info/{id}", conferenceId)
                            .header("Authorization", host.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value(2));
        }
    }

    @Nested
    @DisplayName("入参校验与鉴权")
    class ValidationTests {

    @Test
    @DisplayName("join/leave/mute 缺少 conferenceId 应 400")
    void missingConferenceId_badRequest() throws Exception {
        TestUser user = registerAndLogin("confval");
        mockMvc.perform(post("/conference/join")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/conference/leave")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/conference/mute")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"muted":true}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录 join 应 401")
    void join_unauthorized() throws Exception {
        mockMvc.perform(post("/conference/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conferenceId":1}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }
    }
}
