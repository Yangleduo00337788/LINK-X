package com.linkx.server.service;

import com.linkx.server.controller.vo.NoteVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoteService 笔记服务测试
 */
@DisplayName("NoteService 笔记服务测试")
class NoteServiceTest extends BaseIntegrationTest {

    @Autowired
    private NoteService noteService;

    @Nested
    @DisplayName("list 获取用户笔记测试")
    class ListTests {

        @Test
        @DisplayName("获取用户笔记应成功")
        void list_success() {
            List<NoteVO> notes = noteService.list(1L);
            assertNotNull(notes);
        }
    }
}
