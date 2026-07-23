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
}
