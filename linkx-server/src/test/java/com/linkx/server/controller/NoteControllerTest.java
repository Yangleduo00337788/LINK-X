package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("NoteController integration tests")
class NoteControllerTest extends BaseIntegrationTest {

    private String createNote(TestUser user, String title, String content) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"type\":\"note\"}";
        MvcResult result = mockMvc.perform(post("/notes")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    @Nested
    @DisplayName("POST /notes")
    class CreateNoteTests {

        @Test
        @DisplayName("create success")
        void createNote_success() throws Exception {
            TestUser user = registerAndLogin("noteuser");
            mockMvc.perform(post("/notes")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"note title\",\"content\":\"body\",\"type\":\"note\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("note title"));
        }

        @Test
        @DisplayName("unauthorized create returns 401")
        void createNote_unauthorized() throws Exception {
            mockMvc.perform(post("/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"t\",\"content\":\"c\"}"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("note CRUD")
    class CrudTests {

        @Test
        @DisplayName("list get update delete success")
        void listGetUpdateDelete_success() throws Exception {
            TestUser user = registerAndLogin("notecrud");
            String noteId = createNote(user, "titleA", "contentA");

            mockMvc.perform(get("/notes").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[*].id", hasItem(noteId)));

            mockMvc.perform(get("/notes/{noteId}", noteId).header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("titleA"));

            mockMvc.perform(put("/notes/{noteId}", noteId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"titleB\",\"content\":\"contentB\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("titleB"))
                    .andExpect(jsonPath("$.data.content").value("contentB"));

            mockMvc.perform(delete("/notes/{noteId}", noteId).header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/notes").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[*].id", not(hasItem(noteId))));
        }

        @Test
        @DisplayName("get other users note returns 403")
        void get_forbiddenForOtherUser() throws Exception {
            TestUser owner = registerAndLogin("noteowner");
            TestUser other = registerAndLogin("noteintruder");
            String noteId = createNote(owner, "private", "secret");

            mockMvc.perform(get("/notes/{noteId}", noteId).header("Authorization", other.bearer()))
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    @Nested
    @DisplayName("media upload and resolve")
    class MediaTests {

        @Test
        @DisplayName("empty upload returns 400")
        void upload_emptyFile() throws Exception {
            TestUser user = registerAndLogin("noteupempty");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.png", "image/png", new byte[0]);

            mockMvc.perform(multipart("/notes/upload")
                            .file(file)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("unauthorized upload returns 401")
        void upload_unauthorized() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.png", "image/png", new byte[]{1});

            mockMvc.perform(multipart("/notes/upload").file(file))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("blank media key returns 400")
        void resolveMedia_blankKey() throws Exception {
            TestUser user = registerAndLogin("notemedia");

            mockMvc.perform(get("/notes/media-url")
                            .param("key", "   ")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
