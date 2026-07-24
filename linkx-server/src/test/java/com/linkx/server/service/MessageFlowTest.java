package com.linkx.server.service;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 私聊消息基础流转：发送、拉取、已读、撤回、搜索、会话列表。
 */
@DisplayName("消息流转基础测试")
class MessageFlowTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("发送-拉取-已读-撤回主路径")
    void sendListReadRecall() {
        TestUser a = registerAndLogin("mfa");
        TestUser b = registerAndLogin("mfb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conv.getId());
        msg.setMsgType("text");
        msg.setContent("flow-hello");
        msg.setClientMsgId(UUID.randomUUID().toString());
        MessageVO sent = chatService.sendMessage(a.userId, msg);
        assertNotNull(sent.getId());
        assertEquals("text", sent.getType());

        List<MessageVO> forB = chatService.listMessages(b.userId, conv.getId(), null, 20);
        assertTrue(forB.stream().anyMatch(m -> sent.getId().equals(m.getId())));

        long unread = chatService.getUnreadCount(b.userId, conv.getId());
        assertTrue(unread >= 1);
        chatService.markAsRead(b.userId, conv.getId(), sent.getId());
        assertEquals(0, chatService.getUnreadCount(b.userId, conv.getId()));

        MessageVO recalled = chatService.recallMessage(a.userId, conv.getId(), sent.getId());
        assertEquals("recall", recalled.getType());
    }

    @Test
    @DisplayName("双向交替发消息后双方历史均可见")
    void bidirectionalMessages_visibleToBoth() {
        TestUser a = registerAndLogin("mfba");
        TestUser b = registerAndLogin("mfbb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        sendText(a, conv.getId(), "from-a-1");
        sendText(b, conv.getId(), "from-b-1");
        sendText(a, conv.getId(), "from-a-2");

        List<MessageVO> aHist = chatService.listMessages(a.userId, conv.getId(), null, 50);
        List<MessageVO> bHist = chatService.listMessages(b.userId, conv.getId(), null, 50);
        assertTrue(aHist.stream().anyMatch(m -> "from-b-1".equals(m.getContent())));
        assertTrue(bHist.stream().anyMatch(m -> "from-a-2".equals(m.getContent())));
        assertFalse(chatService.listConversations(a.userId).isEmpty());
    }

    @Test
    @DisplayName("搜索关键字应命中已发消息")
    void search_hitsContent() {
        TestUser a = registerAndLogin("mfsa");
        TestUser b = registerAndLogin("mfsb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);
        sendText(a, conv.getId(), "unique-keyword-xyz-flow");

        assertFalse(chatService.searchMessages(a.userId, "unique-keyword-xyz", null, conv.getId(), null, null, 20).isEmpty());
    }

    @Test
    @DisplayName("非成员拉取消息应失败")
    void listMessages_nonMember_fails() {
        TestUser a = registerAndLogin("mfna");
        TestUser b = registerAndLogin("mfnb");
        TestUser stranger = registerAndLogin("mfns");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        assertThrows(CustomException.class,
                () -> chatService.listMessages(stranger.userId, conv.getId(), null, 20));
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("hi");
        friendService.sendFriendRequest(a.userId, req);
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        friendService.acceptFriendRequest(b.userId, incoming.get(0).getId());
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }

    private void sendText(TestUser sender, Long conversationId, String content) {
        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conversationId);
        msg.setMsgType("text");
        msg.setContent(content);
        chatService.sendMessage(sender.userId, msg);
    }
}
