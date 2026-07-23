package com.linkx.server.service;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * IM 主链路（HTTP/业务层）集成测试：注册登录 → 加好友 → 开私聊 → 发消息 → 拉历史 → 会话列表。
 * <p>
 * WebSocket 通道侧见 {@code com.linkx.server.im.ImMainLinkE2ETest}。
 */
@DisplayName("IM 主链路业务集成 E2E")
class ImMainLinkE2ETest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("好友私聊发消息全链路应成功")
    void friendPrivateChat_sendAndList() throws Exception {
        TestUser a = registerAndLogin("ime2ea");
        TestUser b = registerAndLogin("ime2eb");

        makeFriends(a, b);

        ConversationVO conv = chatService.getOrCreatePrivateConversation(a.userId, b.userId);
        assertNotNull(conv.getId());

        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conv.getId());
        msg.setMsgType("text");
        msg.setContent("e2e-hello");
        MessageVO sent = chatService.sendMessage(a.userId, msg);
        assertNotNull(sent.getId());
        assertEquals("e2e-hello", sent.getContent());

        List<MessageVO> history = chatService.listMessages(b.userId, conv.getId(), null, 20);
        assertTrue(history.stream().anyMatch(m -> "e2e-hello".equals(m.getContent())));

        mockMvc.perform(get("/chat/sessions")
                        .header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/chat/sessions/" + conv.getId() + "/messages")
                        .header("Authorization", b.bearer())
                        .param("limit", "20"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("HTTP 打开私聊接口应返回会话")
    void openPrivateViaHttp() throws Exception {
        TestUser a = registerAndLogin("ime2eh1");
        TestUser b = registerAndLogin("ime2eh2");
        makeFriends(a, b);

        mockMvc.perform(post("/chat/private/" + b.userId)
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @DisplayName("标记已读后未读数应为 0")
    void markRead_clearsUnread() {
        TestUser a = registerAndLogin("ime2er1");
        TestUser b = registerAndLogin("ime2er2");
        makeFriends(a, b);
        ConversationVO conv = chatService.getOrCreatePrivateConversation(a.userId, b.userId);

        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conv.getId());
        msg.setMsgType("text");
        msg.setContent("unread-me");
        MessageVO sent = chatService.sendMessage(b.userId, msg);

        assertTrue(chatService.getUnreadCount(a.userId, conv.getId()) >= 1);
        chatService.markAsRead(a.userId, conv.getId(), sent.getId());
        assertEquals(0, chatService.getUnreadCount(a.userId, conv.getId()));
    }

    private void makeFriends(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("hi");
        friendService.sendFriendRequest(a.userId, req);
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        assertFalse(incoming.isEmpty());
        friendService.acceptFriendRequest(b.userId, incoming.get(0).getId());
    }
}
