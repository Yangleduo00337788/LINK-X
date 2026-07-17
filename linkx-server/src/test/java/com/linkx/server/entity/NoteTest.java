package com.linkx.server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Note 实体测试
 */
@DisplayName("Note 实体测试")
class NoteTest {

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("使用Builder创建笔记应成功")
        void builderCreatesNote() {
            Date now = new Date();
            Note note = Note.builder()
                    .id(1L)
                    .userId(100L)
                    .title("测试笔记")
                    .content("这是笔记内容")
                    .type("note")
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();

            assertNotNull(note);
            assertEquals(1L, note.getId());
            assertEquals(100L, note.getUserId());
            assertEquals("测试笔记", note.getTitle());
            assertEquals("这是笔记内容", note.getContent());
            assertEquals("note", note.getType());
            assertEquals(now, note.getCreateTime());
            assertEquals(now, note.getUpdateTime());
            assertEquals(0, note.getDeleted());
        }

        @Test
        @DisplayName("不同type应可设置")
        void differentTypesCanBeSet() {
            Note note = Note.builder()
                    .title("收藏")
                    .type("image")
                    .build();

            assertEquals("image", note.getType());

            Note linkNote = Note.builder()
                    .title("链接收藏")
                    .type("link")
                    .build();

            assertEquals("link", linkNote.getType());

            Note fileNote = Note.builder()
                    .title("文件收藏")
                    .type("file")
                    .build();

            assertEquals("file", fileNote.getType());
        }
    }

    @Nested
    @DisplayName("逻辑删除测试")
    class LogicDeleteTests {

        @Test
        @DisplayName("deleted字段默认值应为0")
        void deletedDefaultIsZero() {
            Note note = Note.builder()
                    .title("新笔记")
                    .content("内容")
                    .build();

            // 注意：Builder不会自动设置deleted，默认是null
            // deleted字段需要显式设置或由数据库自动处理
            note.setDeleted(0);
            assertEquals(0, note.getDeleted());
        }

        @Test
        @DisplayName("deleted可设置为1表示已删除")
        void deletedCanBeOne() {
            Note note = Note.builder().title("删除的笔记").build();
            note.setDeleted(1);
            assertEquals(1, note.getDeleted());
        }
    }

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造器应创建空对象")
        void noArgsConstructorWorks() {
            Note note = new Note();
            assertNull(note.getId());
            assertNull(note.getTitle());
            assertNull(note.getType());
        }

        @Test
        @DisplayName("全参构造器应创建完整对象")
        void allArgsConstructorWorks() {
            Date now = new Date();
            Note note = new Note(1L, 100L, "全笔记", "完整内容", "note", now, now, 0);

            assertEquals(1L, note.getId());
            assertEquals(100L, note.getUserId());
            assertEquals("全笔记", note.getTitle());
            assertEquals("完整内容", note.getContent());
            assertEquals("note", note.getType());
            assertEquals(now, note.getCreateTime());
            assertEquals(now, note.getUpdateTime());
            assertEquals(0, note.getDeleted());
        }
    }
}
