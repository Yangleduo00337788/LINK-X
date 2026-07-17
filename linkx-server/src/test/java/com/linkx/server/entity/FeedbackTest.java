package com.linkx.server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feedback 实体测试
 */
@DisplayName("Feedback 实体测试")
class FeedbackTest {

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("使用Builder创建对象应成功")
        void builderCreatesObject() {
            Date now = new Date();
            Feedback feedback = Feedback.builder()
                    .id(1L)
                    .userId(100L)
                    .username("testuser")
                    .type("bug")
                    .content("测试内容")
                    .contact("test@example.com")
                    .status("pending")
                    .createTime(now)
                    .build();

            assertNotNull(feedback);
            assertEquals(1L, feedback.getId());
            assertEquals(100L, feedback.getUserId());
            assertEquals("testuser", feedback.getUsername());
            assertEquals("bug", feedback.getType());
            assertEquals("测试内容", feedback.getContent());
            assertEquals("test@example.com", feedback.getContact());
            assertEquals("pending", feedback.getStatus());
            assertEquals(now, feedback.getCreateTime());
        }

        @Test
        @DisplayName("所有字段都应可访问")
        void allFieldsAccessible() {
            Feedback feedback = Feedback.builder()
                    .id(1L)
                    .userId(1L)
                    .username("user1")
                    .type("suggestion")
                    .content("建议内容")
                    .contact("contact@example.com")
                    .status("resolved")
                    .createTime(new Date())
                    .build();

            assertNotNull(feedback.getId());
            assertNotNull(feedback.getUserId());
            assertNotNull(feedback.getUsername());
            assertNotNull(feedback.getType());
            assertNotNull(feedback.getContent());
            assertNotNull(feedback.getStatus());
        }
    }

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造器应创建空对象")
        void noArgsConstructorWorks() {
            Feedback feedback = new Feedback();
            assertNull(feedback.getId());
            assertNull(feedback.getUserId());
            assertNull(feedback.getType());
        }

        @Test
        @DisplayName("全参构造器应创建完整对象")
        void allArgsConstructorWorks() {
            Date now = new Date();
            Feedback feedback = new Feedback(1L, 100L, "user", "bug", "内容", "contact", "open", now);

            assertEquals(1L, feedback.getId());
            assertEquals(100L, feedback.getUserId());
            assertEquals("user", feedback.getUsername());
            assertEquals("bug", feedback.getType());
            assertEquals("内容", feedback.getContent());
            assertEquals("contact", feedback.getContact());
            assertEquals("open", feedback.getStatus());
            assertEquals(now, feedback.getCreateTime());
        }
    }

    @Nested
    @DisplayName("Getter/Setter测试")
    class GetterSetterTests {

        @Test
        @DisplayName("setter应正确设置值")
        void settersWorkCorrectly() {
            Feedback feedback = new Feedback();

            feedback.setId(10L);
            feedback.setUserId(20L);
            feedback.setUsername("setteruser");
            feedback.setType("feature");
            feedback.setContent("新功能建议");
            feedback.setContact("email@test.com");
            feedback.setStatus("closed");

            assertEquals(10L, feedback.getId());
            assertEquals(20L, feedback.getUserId());
            assertEquals("setteruser", feedback.getUsername());
            assertEquals("feature", feedback.getType());
            assertEquals("新功能建议", feedback.getContent());
            assertEquals("email@test.com", feedback.getContact());
            assertEquals("closed", feedback.getStatus());
        }
    }
}
