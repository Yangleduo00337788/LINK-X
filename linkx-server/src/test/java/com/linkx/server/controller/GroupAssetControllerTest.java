package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("GroupAssetController 群资产集成测试")
class GroupAssetControllerTest extends BaseIntegrationTest {

    private long createGroup(TestUser owner, TestUser member) throws Exception {
        String body = String.format("""
                {"name":"资产群","memberIds":[%d]}
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
        mockMvc.perform(get("/group/1/assets"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("列表与创建精华应成功")
    void listAndCreate() throws Exception {
        TestUser owner = registerAndLogin("assetowner");
        TestUser member = registerAndLogin("assetmember");
        long cid = createGroup(owner, member);

        mockMvc.perform(get("/group/{id}/assets", cid)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/group/{id}/assets", cid)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"essence","title":"精华","content":"内容"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
    }
}
