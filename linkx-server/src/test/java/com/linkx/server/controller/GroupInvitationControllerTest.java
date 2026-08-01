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
        ensureFriends(owner, member);
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

    @Test
    @DisplayName("拒绝邀请后可不勾选确认直接拉人入群，且应出现在成员列表")
    void rejectThenDirectAddMembers() throws Exception {
        TestUser owner = registerAndLogin("rejaddo");
        TestUser member = registerAndLogin("rejaddm");
        TestUser invitee = registerAndLogin("rejaddi");
        long cid = createGroup(owner, member);

        String inviteResp = mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d,"message":"来玩"}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long invitationId = objectMapper.readTree(inviteResp).path("data").path("id").asLong();

        mockMvc.perform(post("/group/invitations/{id}/reject", invitationId)
                        .header("Authorization", invitee.bearer()))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/group/{id}/members", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"memberIds":[%d]}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(invitee.userId));

        mockMvc.perform(get("/group/{id}/members", cid)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.userId==" + invitee.userId + ")]").exists());
    }

    @Test
    @DisplayName("拒绝后可再次邀请，并可再次拒绝（不受历史 rejected 唯一键挡住）")
    void rejectThenReinviteThenRejectAgain() throws Exception {
        TestUser owner = registerAndLogin("rejrjo");
        TestUser member = registerAndLogin("rejrm");
        TestUser invitee = registerAndLogin("rejri");
        long cid = createGroup(owner, member);

        String invite1 = mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long id1 = objectMapper.readTree(invite1).path("data").path("id").asLong();

        mockMvc.perform(post("/group/invitations/{id}/reject", id1)
                        .header("Authorization", invitee.bearer()))
                .andExpect(jsonPath("$.code").value(200));

        String invite2 = mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long id2 = objectMapper.readTree(invite2).path("data").path("id").asLong();

        mockMvc.perform(post("/group/invitations/{id}/reject", id2)
                        .header("Authorization", invitee.bearer()))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("ownerApprove 策略下普通成员邀请应 403")
    void invitePolicyOwnerApprove_blocksMember() throws Exception {
        TestUser owner = registerAndLogin("polowner");
        TestUser member = registerAndLogin("polmember");
        TestUser invitee = registerAndLogin("polinvitee");
        long cid = createGroup(owner, member);

        mockMvc.perform(post("/group/{id}/invite-policy", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"ownerApprove\"}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/group/invitations/{cid}", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"inviteeUserId":%d}
                                """, invitee.userId)))
                .andExpect(jsonPath("$.code").value(200));
    }
}
