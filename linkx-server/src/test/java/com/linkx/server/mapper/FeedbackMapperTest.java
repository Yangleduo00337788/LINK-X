package com.linkx.server.mapper;

import com.linkx.server.entity.Feedback;
import com.linkx.server.support.BaseIntegrationTest;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeedbackMapper 数据库访问层测试
 */
@DisplayName("FeedbackMapper 测试")
class FeedbackMapperTest extends BaseIntegrationTest {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Nested
    @DisplayName("CRUD 操作测试")
    class CrudTests {

        @Test
        @DisplayName("插入反馈应成功")
        void insertFeedback_success() {
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("testuser")
                    .type("bug")
                    .content("测试反馈内容")
                    .contact("test@example.com")
                    .status("pending")
                    .build();

            int rows = feedbackMapper.insert(feedback);

            assertEquals(1, rows);
            assertNotNull(feedback.getId());
            assertTrue(feedback.getId() > 0);
        }

        @Test
        @DisplayName("根据ID查询反馈应成功")
        void selectById_success() {
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("finduser")
                    .type("suggestion")
                    .content("查找测试")
                    .status("open")
                    .build();
            feedbackMapper.insert(feedback);

            Feedback found = feedbackMapper.selectOneById(feedback.getId());

            assertNotNull(found);
            assertEquals(feedback.getId(), found.getId());
            assertEquals("suggestion", found.getType());
        }

        @Test
        @DisplayName("更新反馈状态应成功")
        void updateStatus_success() {
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("updateuser")
                    .type("bug")
                    .content("更新测试")
                    .status("pending")
                    .build();
            feedbackMapper.insert(feedback);

            feedback.setStatus("resolved");
            int rows = feedbackMapper.update(feedback);

            assertEquals(1, rows);

            Feedback updated = feedbackMapper.selectOneById(feedback.getId());
            assertEquals("resolved", updated.getStatus());
        }

        @Test
        @DisplayName("删除反馈应成功")
        void deleteFeedback_success() {
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("deleteuser")
                    .type("other")
                    .content("删除测试")
                    .status("closed")
                    .build();
            feedbackMapper.insert(feedback);

            int rows = feedbackMapper.deleteById(feedback.getId());

            assertEquals(1, rows);
            assertNull(feedbackMapper.selectOneById(feedback.getId()));
        }
    }

    @Nested
    @DisplayName("条件查询测试")
    class QueryTests {

        @Test
        @DisplayName("按用户ID查询反馈应成功")
        void selectByUserId_success() {
            Long userId = 100L + (long)(Math.random() * 1000);
            for (int i = 0; i < 3; i++) {
                Feedback feedback = Feedback.builder()
                        .userId(userId)
                        .username("user" + userId)
                        .type("bug")
                        .content("用户反馈" + i)
                        .status("pending")
                        .build();
                feedbackMapper.insert(feedback);
            }

            QueryWrapper query = QueryWrapper.create()
                    .where(Feedback::getUserId).eq(userId);
            List<Feedback> list = feedbackMapper.selectListByQuery(query);

            assertEquals(3, list.size());
            list.forEach(f -> assertEquals(userId, f.getUserId()));
        }

        @Test
        @DisplayName("按类型查询反馈应成功")
        void selectByType_success() {
            String type = "feature_" + System.nanoTime();
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("typeuser")
                    .type(type)
                    .content("类型测试")
                    .status("open")
                    .build();
            feedbackMapper.insert(feedback);

            QueryWrapper query = QueryWrapper.create()
                    .where(Feedback::getType).eq(type);
            List<Feedback> list = feedbackMapper.selectListByQuery(query);

            assertEquals(1, list.size());
            assertEquals(type, list.get(0).getType());
        }

        @Test
        @DisplayName("按状态查询反馈应成功")
        void selectByStatus_success() {
            Feedback feedback = Feedback.builder()
                    .userId(1L)
                    .username("statususer")
                    .type("bug")
                    .content("状态测试")
                    .status("resolved")
                    .build();
            feedbackMapper.insert(feedback);

            QueryWrapper query = QueryWrapper.create()
                    .where(Feedback::getStatus).eq("resolved");
            List<Feedback> list = feedbackMapper.selectListByQuery(query);

            assertTrue(list.size() >= 1);
            assertTrue(list.stream().allMatch(f -> "resolved".equals(f.getStatus())));
        }
    }

    @Nested
    @DisplayName("统计查询测试")
    class CountTests {

        @Test
        @DisplayName("selectAll应返回列表")
        void selectAll_works() {
            List<Feedback> list = feedbackMapper.selectAll();

            assertNotNull(list);
        }
    }
}
