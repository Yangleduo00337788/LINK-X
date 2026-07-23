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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息排序：发送后 id 递增；拉取后可按 id/时间还原发送顺序。
 */
@DisplayName("消息排序测试")
class MessageOrderingTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("连续发送后消息 id 应递增")
    void sentIds_areIncreasing() {
        TestUser a = registerAndLogin("orda");
        TestUser b = registerAndLogin("ordb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        List<MessageVO> sent = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            sent.add(sendText(a, conv.getId(), "ord-" + i));
        }

        for (int i = 1; i < sent.size(); i++) {
            assertNotNull(sent.get(i).getId());
            assertTrue(sent.get(i).getId() > sent.get(i - 1).getId(),
                    "后发消息 id 应更大: " + sent.get(i - 1).getId() + " -> " + sent.get(i).getId());
        }
    }

    @Test
    @DisplayName("listMessages 应返回全部消息且最大 id 对应最后发送内容")
    void listMessages_containsAllInStableOrder() {
        TestUser a = registerAndLogin("ordla");
        TestUser b = registerAndLogin("ordlb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        for (int i = 1; i <= 4; i++) {
            sendText(a, conv.getId(), "list-ord-" + i);
        }

        List<MessageVO> listed = chatService.listMessages(b.userId, conv.getId(), null, 50);
        List<MessageVO> ours = listed.stream()
                .filter(m -> m.getContent() != null && m.getContent().startsWith("list-ord-"))
                .toList();
        assertEquals(4, ours.size());

        // 不依赖 list 的正/倒序实现细节：按 id 排序后应与发送顺序一致
        List<String> byIdAsc = ours.stream()
                .sorted(Comparator.comparing(MessageVO::getId))
                .map(MessageVO::getContent)
                .toList();
        assertEquals(List.of("list-ord-1", "list-ord-2", "list-ord-3", "list-ord-4"), byIdAsc);

        MessageVO newest = ours.stream().max(Comparator.comparing(MessageVO::getId)).orElseThrow();
        assertEquals("list-ord-4", newest.getContent());
    }

    @Test
    @DisplayName("按 id 升序重排后应与发送顺序一致")
    void ascendingById_matchesSendOrder() {
        TestUser a = registerAndLogin("ordaa");
        TestUser b = registerAndLogin("ordab");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        List<String> expected = List.of("s1", "s2", "s3");
        for (String content : expected) {
            sendText(a, conv.getId(), content);
        }

        List<MessageVO> listed = chatService.listMessages(a.userId, conv.getId(), null, 50);
        List<String> chronological = listed.stream()
                .filter(m -> expected.contains(m.getContent()))
                .sorted(Comparator.comparing(MessageVO::getId))
                .map(MessageVO::getContent)
                .toList();
        assertEquals(expected, chronological);
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

    private MessageVO sendText(TestUser sender, Long conversationId, String content) {
        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conversationId);
        msg.setMsgType("text");
        msg.setContent(content);
        return chatService.sendMessage(sender.userId, msg);
    }
}
