package com.linkx.server.controller;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FriendService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ChatController 聊天控制器集成测试")
class ChatControllerTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Nested
    @DisplayName("GET /chat/sessions 获取会话列表测试")
    class ListSessionsTests {

        @Test
        @DisplayName("获取会话列表应成功")
        void listSessions_success() throws Exception {
            TestUser user = registerAndLogin("chatuser");

            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录获取会话列表应返回401")
        void listSessions_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/sessions"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /chat/search 搜索消息")
    class SearchTests {

        @Test
        @DisplayName("搜索应返回数组")
        void search_success() throws Exception {
            TestUser user = registerAndLogin("chatsearch");

            mockMvc.perform(get("/chat/search")
                            .param("q", "hello")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录搜索应返回401")
        void search_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/search").param("q", "hello"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /chat/private/{friendId}")
    class OpenPrivateTests {

        @Test
        @DisplayName("非好友打开私聊应失败")
        void openPrivate_notFriend() throws Exception {
            TestUser a = registerAndLogin("chata");
            TestUser b = registerAndLogin("chatb");

            mockMvc.perform(post("/chat/private/{friendId}", b.userId)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(anyOf(is(400), is(403), is(404))));
        }
    }

    @Nested
    @DisplayName("POST /chat/sessions/{conversationId}/read 已读回执测试")
    class ReadReceiptTests {

        @Test
        @DisplayName("标记已读并返回未读数应成功")
        void markAsRead_success() throws Exception {
            TestUser a = registerAndLogin("reada");
            TestUser b = registerAndLogin("readb");

            SendFriendRequestDTO req = new SendFriendRequestDTO();
            req.setUsername(b.username);
            req.setMessage("hi");
            friendService.sendFriendRequest(a.userId, req);
            Long requestId = friendService.listIncomingRequests(b.userId).get(0).getId();
            friendService.acceptFriendRequest(b.userId, requestId);

            ConversationVO conv = chatService.getOrCreatePrivateConversation(a.userId, b.userId);

            // 先发送一条新消息给 a
            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("unread-1");
            MessageVO sent = chatService.sendMessage(b.userId, msg);

            // 标记已读，期望返回更新后的 unreadCount（此时 a 已读，对 a 为 0）
            mockMvc.perform(post("/chat/sessions/{conversationId}/read", conv.getId())
                            .param("lastMessageId", String.valueOf(sent.getId()))
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/chat/sessions/{conversationId}/unread", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(0));
        }
    }
}
