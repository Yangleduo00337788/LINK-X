package com.linkx.server.service;

import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService 聊天服务测试
 */
@DisplayName("ChatService 聊天服务测试")
class ChatServiceTest extends BaseIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Nested
    @DisplayName("listConversations 获取会话列表测试")
    class ListConversationsTests {

        @Test
        @DisplayName("获取会话列表应成功")
        void listConversations_success() {
            List<ConversationVO> conversations = chatService.listConversations(1L);
            assertNotNull(conversations);
        }
    }
}
