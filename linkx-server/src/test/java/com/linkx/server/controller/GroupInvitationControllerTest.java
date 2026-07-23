package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("GroupInvitationController 群邀请集成测试")
class GroupInvitationControllerTest extends BaseIntegrationTest {

    private long createGroup(TestUser owner, TestUser member) throws Exception {
        String body = String.format("""
                {"name":"邀请群","memberIds":[%d]}
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
        mockMvc.perform(get("/group/invitations"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("邀请/列表/接受/拒绝流程")
    void inviteAcceptRejectFlow() throws Exception {
        TestUser owner = registerAndLogin("invowner");
        TestUser member = registerAndLogin("invmember");
        TestUser invitee = registerAndLogin("invitee");
        long cid = createGroup(owner, member);

        mockMvc.perform(get("/group/invitations").header("Authorization", invitee.bearer()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        String inviteResp = mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d,"message":"来玩"}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long invitationId = objectMapper.readTree(inviteResp).path("data").path("id").asLong();

        // 再邀请另一个用户用于拒绝
        TestUser invitee2 = registerAndLogin("invitee2");
        String invite2 = mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d}
                                """, invitee2.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long invitationId2 = objectMapper.readTree(invite2).path("data").path("id").asLong();

        mockMvc.perform(post("/group/invitations/{id}/accept", invitationId)
                        .header("Authorization", invitee.bearer()))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/group/invitations/{id}/reject", invitationId2)
                        .header("Authorization", invitee2.bearer()))
                .andExpect(jsonPath("$.code").value(200));
    }
}
