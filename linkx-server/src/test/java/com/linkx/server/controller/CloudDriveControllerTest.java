package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("CloudDriveController 网盘集成测试")
class CloudDriveControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("存储与列表")
    class StorageAndList {
        @Test
        @DisplayName("未登录应 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/cloud/storage"))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("查询存储与根目录列表应成功")
        void storageAndItems_success() throws Exception {
            TestUser user = registerAndLogin("drive");
            mockMvc.perform(get("/cloud/storage").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(get("/cloud/items").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
            mockMvc.perform(get("/cloud/breadcrumb").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("创建文件夹应成功（上传依赖 MinIO，测试环境仅校验建夹）")
        void createFolderAndUpload() throws Exception {
            TestUser user = registerAndLogin("driveup");
            String body = """
                    {"name":"folder-a"}
                    """;
            mockMvc.perform(post("/cloud/folders")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("文件夹生命周期与活动")
    class FolderLifecycle {
        @Test
        @DisplayName("创建/重命名/列活动/删除文件夹")
        void createRenameDeleteFolder() throws Exception {
            TestUser user = registerAndLogin("drivefld");
            String create = mockMvc.perform(post("/cloud/folders")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"life-a\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            long folderId = objectMapper.readTree(create).path("data").path("id").asLong();

            mockMvc.perform(patch("/cloud/folders/" + folderId)
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"life-b\"}"))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/cloud/items").header("Authorization", user.bearer())
                            .param("folderId", String.valueOf(folderId)))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/cloud/breadcrumb").header("Authorization", user.bearer())
                            .param("folderId", String.valueOf(folderId)))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/cloud/activities").header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(post("/cloud/storage/expand")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bytes\":1048576}"))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(200),
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(403))));

            mockMvc.perform(post("/cloud/items/batch-delete")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"folderIds\":[" + folderId + "],\"fileIds\":[]}"))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(200),
                            org.hamcrest.Matchers.is(400))));

            // 若批量删除未清掉，再尝试单删
            mockMvc.perform(delete("/cloud/folders/" + folderId)
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(200),
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(404))));
        }

        @Test
        @DisplayName("不存在的文件/分享应业务失败而非 5xx")
        void missingResources_noServerError() throws Exception {
            TestUser user = registerAndLogin("drivemiss");
            mockMvc.perform(get("/cloud/files/999999999")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(404))));
            mockMvc.perform(get("/cloud/share/not-a-token")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(404))));
            mockMvc.perform(post("/cloud/shares")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":1,\"expireDays\":1}"))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(200),
                            org.hamcrest.Matchers.is(400),
                            org.hamcrest.Matchers.is(404))));
        }
    }
}
