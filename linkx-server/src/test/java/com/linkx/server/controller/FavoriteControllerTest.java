package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("FavoriteController integration tests")
class FavoriteControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("favorite CRUD and storage")
    class CrudTests {

        @Test
        @DisplayName("create list update delete success")
        void createListUpdateDelete_success() throws Exception {
            TestUser user = registerAndLogin("favuser");

            MvcResult createResult = mockMvc.perform(post("/favorites")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"fav link\",\"content\":\"https://example.com\",\"type\":\"link\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("fav link"))
                    .andReturn();
            String favId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").get("id").asText();

            mockMvc.perform(get("/favorites").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[*].id", hasItem(favId)));

            mockMvc.perform(get("/favorites/storage").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(put("/favorites/{id}", favId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"renamed\",\"content\":\"https://example.com/u\",\"type\":\"link\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("renamed"));

            mockMvc.perform(delete("/favorites/{id}", favId).header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/favorites").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[*].id", not(hasItem(favId))));
        }

        @Test
        @DisplayName("unauthorized create returns 401")
        void create_unauthorized() throws Exception {
            mockMvc.perform(post("/favorites")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"x\",\"content\":\"y\",\"type\":\"link\"}"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("tags")
    class TagTests {

        @Test
        @DisplayName("create and list custom tag")
        void createAndListTags_success() throws Exception {
            TestUser user = registerAndLogin("favtag");

            mockMvc.perform(post("/favorites/tags")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"CustomTagXYZ\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("CustomTagXYZ"));

            mockMvc.perform(get("/favorites/tags").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
