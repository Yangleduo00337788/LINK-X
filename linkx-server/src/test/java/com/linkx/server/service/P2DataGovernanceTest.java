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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P2 脱敏/风暴/游标分页")
class P2DataGovernanceTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageStormService messageStormService;

    @Nested
    @DisplayName("历史消息 id 游标分页")
    class CursorPaging {

        @Test
        @DisplayName("beforeMessageId 应按 id 稳定翻页且无重叠")
        void idCursor_noOverlap() {
            TestUser a = registerAndLogin("p2cur");
            TestUser b = registerAndLogin("p2cub");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            List<Long> allIds = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                MessageVO m = sendText(a, conv.getId(), "page-" + i);
                allIds.add(m.getId());
            }

            List<MessageVO> page1 = chatService.listMessages(a.userId, conv.getId(), null, 3);
            assertEquals(3, page1.size());
            Long oldestInPage1 = page1.get(0).getId();

            List<MessageVO> page2 = chatService.listMessages(a.userId, conv.getId(), oldestInPage1, 3);
            assertEquals(3, page2.size());

            Set<Long> p1 = new HashSet<>();
            page1.forEach(m -> p1.add(m.getId()));
            for (MessageVO m : page2) {
                assertFalse(p1.contains(m.getId()), "翻页不应重叠");
                assertTrue(m.getId() < oldestInPage1);
            }

            // 页内按 id 升序
            for (int i = 1; i < page1.size(); i++) {
                assertTrue(page1.get(i).getId() > page1.get(i - 1).getId());
            }
        }
    }

    @Nested
    @DisplayName("消息风暴落库")
    class StormPersist {

        @Test
        @DisplayName("用户级超限应落库且返回 true")
        void userStorm_persistsEvent() throws InterruptedException {
            TestUser user = registerAndLogin("p2stm");
            long before = messageStormService.countRecentEvents(user.userId);

            boolean blocked = false;
            for (int i = 0; i < 35; i++) {
                if (messageStormService.checkAndRecordUserStorm(user.userId)) {
                    blocked = true;
                    break;
                }
            }
            assertTrue(blocked, "超过阈值应触发风暴");

            // 异步审计可忽略；事件表同步 insert
            long deadline = System.currentTimeMillis() + 2000;
            long after = before;
            while (System.currentTimeMillis() < deadline) {
                after = messageStormService.countRecentEvents(user.userId);
                if (after > before) {
                    break;
                }
                Thread.sleep(50);
            }
            assertTrue(after > before, "应写入 im_message_storm_event");
        }

        @Test
        @DisplayName("大群超限应 429 并落库")
        void groupStorm_throwsAndPersists() {
            TestUser user = registerAndLogin("p2stg");
            long before = messageStormService.countRecentEvents(user.userId);
            Long fakeConv = 900001L;

            CustomException ex = null;
            for (int i = 0; i < 12; i++) {
                try {
                    messageStormService.checkAndRecordGroupStorm(user.userId, fakeConv, 600);
                } catch (CustomException e) {
                    ex = e;
                    break;
                }
            }
            assertNotNull(ex);
            assertEquals(429, ex.getCode());
            assertTrue(messageStormService.countRecentEvents(user.userId) > before);
        }
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
        msg.setClientMsgId(UUID.randomUUID().toString());
        return chatService.sendMessage(sender.userId, msg);
    }
}
