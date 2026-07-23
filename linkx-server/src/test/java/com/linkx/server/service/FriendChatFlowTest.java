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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 好友申请全流程 + 私聊消息关键路径。
 */
@DisplayName("Friend/Chat 高风险路径集成测试")
class FriendChatFlowTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("申请-同意-私聊-发消息-撤回")
    void friendThenPrivateChat() {
        TestUser a = registerAndLogin("flowa");
        TestUser b = registerAndLogin("flowb");

        SendFriendRequestDTO dto = new SendFriendRequestDTO();
        dto.setUsername(b.username);
        dto.setMessage("hi");
        friendService.sendFriendRequest(a.userId, dto);

        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        assertFalse(incoming.isEmpty());
        Long requestId = incoming.get(0).getId();
        friendService.acceptFriendRequest(b.userId, requestId);

        assertTrue(friendService.listFriends(a.userId).stream()
                .anyMatch(f -> f.getUserId().equals(b.userId)));

        ConversationVO conv = chatService.getOrCreatePrivateConversation(a.userId, b.userId);
        assertNotNull(conv.getId());

        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conv.getId());
        msg.setMsgType("text");
        msg.setContent("hello friend");
        MessageVO sent = chatService.sendMessage(a.userId, msg);
        assertEquals("text", sent.getType());
        assertNotNull(sent.getId());

        List<MessageVO> history = chatService.listMessages(a.userId, conv.getId(), null, 20);
        assertFalse(history.isEmpty());

        MessageVO recalled = chatService.recallMessage(a.userId, conv.getId(), sent.getId());
        assertEquals("recall", recalled.getType());

        assertNotNull(chatService.searchMessages(a.userId, "friend", null, null, 20));

        List<ConversationVO> sessions = chatService.listConversations(a.userId);
        assertFalse(sessions.isEmpty());

        friendService.deleteFriend(a.userId, b.userId);
        assertTrue(friendService.listFriends(a.userId).stream()
                .noneMatch(f -> f.getUserId().equals(b.userId)));
    }

    @Test
    @DisplayName("非好友打开私聊应失败")
    void openPrivate_notFriend() {
        TestUser a = registerAndLogin("nfa");
        TestUser b = registerAndLogin("nfb");
        assertThrows(CustomException.class,
                () -> chatService.getOrCreatePrivateConversation(a.userId, b.userId));
    }

    @Test
    @DisplayName("拒绝好友申请")
    void rejectRequest() {
        TestUser a = registerAndLogin("reja");
        TestUser b = registerAndLogin("rejb");
        SendFriendRequestDTO dto = new SendFriendRequestDTO();
        dto.setUsername(b.username);
        friendService.sendFriendRequest(a.userId, dto);
        Long requestId = friendService.listIncomingRequests(b.userId).get(0).getId();
        friendService.rejectFriendRequest(b.userId, requestId);
        assertTrue(friendService.listFriends(a.userId).isEmpty());
    }
}
