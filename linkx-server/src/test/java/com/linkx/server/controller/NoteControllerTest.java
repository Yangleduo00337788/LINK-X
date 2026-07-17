package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NoteController 笔记控制器集成测试
 */
@DisplayName("NoteController 笔记控制器集成测试")
class NoteControllerTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /notes 创建笔记测试")
    class CreateNoteTests {

        @Test
        @DisplayName("创建笔记应成功")
        void createNote_success() throws Exception {
            TestUser user = registerAndLogin("noteuser");

            String body = """
                {
                    "title": "测试笔记",
                    "content": "这是笔记内容",
                    "type": "note"
                }
                """;

            mockMvc.perform(post("/notes")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("测试笔记"));
        }

        @Test
        @DisplayName("未登录创建笔记应返回401")
        void createNote_unauthorized() throws Exception {
            String body = """
                {
                    "title": "测试笔记",
                    "content": "内容"
                }
                """;

            mockMvc.perform(post("/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
