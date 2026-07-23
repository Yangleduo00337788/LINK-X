package com.linkx.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("GroupAnnouncementController 群公告集成测试")
class GroupAnnouncementControllerTest extends BaseIntegrationTest {

    private long createGroup(TestUser owner, TestUser member) throws Exception {
        String body = String.format("""
                {"name":"公告群","memberIds":[%d]}
                """, member.userId);
        String resp = mockMvc.perform(post("/group")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("未登录应 401")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/group/1/announcements"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("列表/展示/创建公告应成功")
    void listDisplayCreate() throws Exception {
        TestUser owner = registerAndLogin("annowner");
        TestUser member = registerAndLogin("annmember");
        long cid = createGroup(owner, member);

        mockMvc.perform(get("/group/{id}/announcements", cid)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/group/{id}/announcements/display", cid)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.code").value(200));

        String createResp = mockMvc.perform(post("/group/{id}/announcements", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"大家好","pinned":true}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(createResp).path("data");
        long aid = data.path("id").asLong();

        mockMvc.perform(put("/group/{cid}/announcements/{aid}", cid, aid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"更新公告","pinned":false}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/group/{cid}/announcements/{aid}", cid, aid)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.code").value(200));
    }
}
