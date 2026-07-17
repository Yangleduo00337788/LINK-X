package com.linkx.server.service;

import com.linkx.server.entity.Feedback;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeedbackService 反馈服务测试
 */
@DisplayName("FeedbackService 反馈服务测试")
class FeedbackServiceTest extends BaseIntegrationTest {

    @Autowired
    private FeedbackService feedbackService;

    @Nested
    @DisplayName("create 提交反馈测试")
    class CreateTests {

        @Test
        @DisplayName("提交反馈应成功")
        void create_success() {
            Feedback feedback = feedbackService.create(1L, "testuser", "bug", "测试反馈", "test@example.com");
            assertNotNull(feedback);
            assertEquals("bug", feedback.getType());
            assertEquals("测试反馈", feedback.getContent());
        }
    }

    @Nested
    @DisplayName("listByUser 获取用户反馈列表测试")
    class ListByUserTests {

        @Test
        @DisplayName("获取用户反馈列表应成功")
        void listByUser_success() {
            List<Feedback> list = feedbackService.listByUser(1L);
            assertNotNull(list);
        }
    }
}
