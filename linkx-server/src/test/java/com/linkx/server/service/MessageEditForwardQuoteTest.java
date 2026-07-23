package com.linkx.server.service;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 编辑 / 转发 / 引用回复深化测试（FriendChatFlowTest 已覆盖编辑与转发主干）。
 */
@DisplayName("消息编辑转发引用测试")
class MessageEditForwardQuoteTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Nested
    @DisplayName("引用回复")
    class Quote {

        @Test
        @DisplayName("引用文本消息应带上 quote 元数据")
        void quoteText_success() {
            TestUser a = registerAndLogin("qta");
            TestUser b = registerAndLogin("qtb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            MessageVO original = sendText(a, conv.getId(), "original quote target");

            SendMessageDTO reply = new SendMessageDTO();
            reply.setConversationId(conv.getId());
            reply.setMsgType("text");
            reply.setContent("this is a reply");
            MessageVO quoted = chatService.quoteMessage(b.userId, conv.getId(), original.getId(), reply);

            assertEquals("this is a reply", quoted.getContent());
            assertEquals(original.getId(), quoted.getQuoteMessageId());
            assertEquals(conv.getId(), quoted.getQuoteConversationId());
            assertEquals(a.userId, quoted.getQuoteSenderId());
            assertEquals("original quote target", quoted.getQuoteContent());
            assertEquals("text", quoted.getQuoteType());
        }

        @Test
        @DisplayName("引用已撤回消息应失败")
        void quoteRecalled_shouldFail() {
            TestUser a = registerAndLogin("qra");
            TestUser b = registerAndLogin("qrb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            MessageVO original = sendText(a, conv.getId(), "will recall");
            chatService.recallMessage(a.userId, conv.getId(), original.getId());

            SendMessageDTO reply = new SendMessageDTO();
            reply.setConversationId(conv.getId());
            reply.setMsgType("text");
            reply.setContent("reply");
            assertThrows(CustomException.class,
                    () -> chatService.quoteMessage(b.userId, conv.getId(), original.getId(), reply));
        }

        @Test
        @DisplayName("引用不存在的消息应失败")
        void quoteMissing_shouldFail() {
            TestUser a = registerAndLogin("qma");
            TestUser b = registerAndLogin("qmb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            SendMessageDTO reply = new SendMessageDTO();
            reply.setConversationId(conv.getId());
            reply.setMsgType("text");
            reply.setContent("reply");
            assertThrows(CustomException.class,
                    () -> chatService.quoteMessage(a.userId, conv.getId(), 999999999L, reply));
        }

        @Test
        @DisplayName("HTTP quote 接口应返回 200 且含引用字段")
        void quoteViaHttp() throws Exception {
            TestUser a = registerAndLogin("qha");
            TestUser b = registerAndLogin("qhb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);
            MessageVO original = sendText(a, conv.getId(), "http-quote-src");

            String body = objectMapper.writeValueAsString(Map.of(
                    "conversationId", conv.getId(),
                    "msgType", "text",
                    "content", "http-quote-reply"
            ));
            mockMvc.perform(post("/chat/sessions/" + conv.getId() + "/messages/" + original.getId() + "/quote")
                            .header("Authorization", b.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.quoteMessageId").value(original.getId()))
                    .andExpect(jsonPath("$.data.content").value("http-quote-reply"));
        }
    }

    @Nested
    @DisplayName("编辑与转发补充")
    class EditAndForwardExtra {

        @Test
        @DisplayName("编辑后再拉取历史应看到新内容与 edited 标记")
        void editThenList_showsEdited() {
            TestUser a = registerAndLogin("eda3");
            TestUser b = registerAndLogin("edb3");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            MessageVO sent = sendText(a, conv.getId(), "before-edit");
            chatService.editMessage(a.userId, conv.getId(), sent.getId(), "after-edit");

            List<MessageVO> history = chatService.listMessages(b.userId, conv.getId(), null, 20);
            MessageVO found = history.stream()
                    .filter(m -> sent.getId().equals(m.getId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("after-edit", found.getContent());
            assertTrue(Boolean.TRUE.equals(found.getEdited()));
        }

        @Test
        @DisplayName("转发后目标会话历史应包含转发消息")
        void forwardThenListInTarget() {
            TestUser a = registerAndLogin("fwa3");
            TestUser b = registerAndLogin("fwb3");
            TestUser c = registerAndLogin("fwc3");
            makeFriends(a, b);
            makeFriends(a, c);
            ConversationVO ab = chatService.getOrCreatePrivateConversation(a.userId, b.userId);
            ConversationVO ac = chatService.getOrCreatePrivateConversation(a.userId, c.userId);

            MessageVO sent = sendText(a, ab.getId(), "fwd-payload");
            MessageVO forwarded = chatService.forwardMessage(a.userId, ab.getId(), sent.getId(), ac.getId());

            assertEquals(ac.getId(), forwarded.getConversationId());
            assertEquals(sent.getId(), forwarded.getForwardFromMessageId());

            List<MessageVO> acHistory = chatService.listMessages(c.userId, ac.getId(), null, 20);
            assertTrue(acHistory.stream().anyMatch(m -> "fwd-payload".equals(m.getContent())));
        }
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        makeFriends(a, b);
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }

    private void makeFriends(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("hi");
        friendService.sendFriendRequest(a.userId, req);
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        friendService.acceptFriendRequest(b.userId, incoming.get(0).getId());
    }

    private MessageVO sendText(TestUser sender, Long conversationId, String content) {
        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(conversationId);
        msg.setMsgType("text");
        msg.setContent(content);
        return chatService.sendMessage(sender.userId, msg);
    }
}
