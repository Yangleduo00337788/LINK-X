package com.linkx.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MomentsController integration tests")
class MomentsControllerTest extends BaseIntegrationTest {

    private String publishPost(TestUser user, String content) throws Exception {
        String body = "{\"content\":\"" + content + "\"}";
        MvcResult result = mockMvc.perform(post("/moments")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("id").asText();
    }

    @Nested
    @DisplayName("POST /moments")
    class PublishMomentsTests {

        @Test
        @DisplayName("publish success")
        void publishMoments_success() throws Exception {
            TestUser user = registerAndLogin("momentsuser");
            mockMvc.perform(post("/moments")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"nice weather\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").value("nice weather"));
        }

        @Test
        @DisplayName("unauthorized publish returns 401")
        void publishMoments_unauthorized() throws Exception {
            mockMvc.perform(post("/moments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"x\"}"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /moments list/search/paging")
    class ListMomentsTests {

        @Test
        @DisplayName("list contains own post")
        void list_containsOwnPost() throws Exception {
            TestUser user = registerAndLogin("momlist");
            String postId = publishPost(user, "hello moments list");

            mockMvc.perform(get("/moments")
                            .header("Authorization", user.bearer())
                            .param("limit", "10"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[*].id", hasItem(postId)));
        }

        @Test
        @DisplayName("search filters by keyword")
        void list_searchByQuery() throws Exception {
            TestUser user = registerAndLogin("momsearch");
            publishPost(user, "unique keyword AlphaXYZ");
            publishPost(user, "another normal post");

            mockMvc.perform(get("/moments")
                            .header("Authorization", user.bearer())
                            .param("q", "AlphaXYZ")
                            .param("limit", "20"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data[*].content", everyItem(containsString("AlphaXYZ"))));
        }

        @Test
        @DisplayName("beforeId pagination excludes newer id")
        void list_paginationWithBeforeId() throws Exception {
            TestUser user = registerAndLogin("mompaging");
            String olderId = publishPost(user, "older post");
            String newerId = publishPost(user, "newer post");

            mockMvc.perform(get("/moments")
                            .header("Authorization", user.bearer())
                            .param("beforeId", newerId)
                            .param("limit", "20"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[*].id", hasItem(olderId)))
                    .andExpect(jsonPath("$.data[*].id", not(hasItem(newerId))));
        }

        @Test
        @DisplayName("list by user success")
        void listByUser_success() throws Exception {
            TestUser user = registerAndLogin("mombyuser");
            String postId = publishPost(user, "my profile post");

            mockMvc.perform(get("/moments/user/{userId}", user.userId)
                            .header("Authorization", user.bearer())
                            .param("limit", "10"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[*].id", hasItem(postId)));
        }
    }

    @Nested
    @DisplayName("PUT /moments/{id}")
    class UpdateMomentsTests {

        @Test
        @DisplayName("author can update")
        void update_success() throws Exception {
            TestUser user = registerAndLogin("momedit");
            String postId = publishPost(user, "original");

            mockMvc.perform(put("/moments/{postId}", postId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"updated\",\"location\":\"SZ\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").value("updated"))
                    .andExpect(jsonPath("$.data.location").value("SZ"));
        }

        @Test
        @DisplayName("non-author update returns 403")
        void update_forbiddenForOtherUser() throws Exception {
            TestUser author = registerAndLogin("momauthor");
            TestUser other = registerAndLogin("momother");
            String postId = publishPost(author, "locked");

            mockMvc.perform(put("/moments/{postId}", postId)
                            .header("Authorization", other.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"hack\"}"))
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    @Nested
    @DisplayName("like / comment / delete")
    class InteractTests {

        @Test
        @DisplayName("like and unlike success")
        void likeAndUnlike_success() throws Exception {
            TestUser user = registerAndLogin("momlike");
            String postId = publishPost(user, "like me");

            mockMvc.perform(post("/moments/{postId}/like", postId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(delete("/moments/{postId}/like", postId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("nested reply returns parentId and replyToNickname")
        void comment_nestedReply() throws Exception {
            TestUser user = registerAndLogin("momcomment");
            String postId = publishPost(user, "welcome comments");

            MvcResult parentResult = mockMvc.perform(post("/moments/{postId}/comment", postId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"parent comment\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").value("parent comment"))
                    .andReturn();
            JsonNode parentData = objectMapper.readTree(parentResult.getResponse().getContentAsString()).get("data");
            String parentCommentId = parentData.get("id").asText();
            String authorNickname = parentData.get("nickname").asText();

            mockMvc.perform(post("/moments/{postId}/comment", postId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"child reply\",\"parentId\":" + parentCommentId + "}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.parentId").value(parentCommentId))
                    .andExpect(jsonPath("$.data.replyToNickname").value(authorNickname));
        }

        @Test
        @DisplayName("delete own comment success")
        void deleteComment_success() throws Exception {
            TestUser user = registerAndLogin("momdelcmt");
            String postId = publishPost(user, "with comment");

            MvcResult commentResult = mockMvc.perform(post("/moments/{postId}/comment", postId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"to delete\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            String commentId = objectMapper.readTree(commentResult.getResponse().getContentAsString())
                    .get("data").get("id").asText();

            mockMvc.perform(delete("/moments/comment/{commentId}", commentId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("delete own post success")
        void deletePost_success() throws Exception {
            TestUser user = registerAndLogin("momdelpost");
            String postId = publishPost(user, "to delete post");

            mockMvc.perform(delete("/moments/{postId}", postId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("POST /moments/upload validation")
    class UploadTests {

        @Test
        @DisplayName("empty file returns 400")
        void upload_emptyFile() throws Exception {
            TestUser user = registerAndLogin("momupempty");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            mockMvc.perform(multipart("/moments/upload")
                            .file(file)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("invalid content type returns 400")
        void upload_invalidContentType() throws Exception {
            TestUser user = registerAndLogin("momupbad");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/moments/upload")
                            .file(file)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("unauthorized upload returns 401")
        void upload_unauthorized() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

            mockMvc.perform(multipart("/moments/upload").file(file))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
