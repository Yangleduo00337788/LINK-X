package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("ConferenceController 入参校验")
class ConferenceControllerTest extends BaseIntegrationTest {

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
