package com.linkx.server.service;

import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupJoinRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.im.ImWsFrame;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对齐缺口冒烟：已读、送达回执、拉黑、入群审批。
 */
@DisplayName("对齐缺口冒烟")
class AlignmentGapSmokeTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private ImMessagePushService pushService;
    @Autowired
    private ImMessageMapper messageMapper;

    @Test
    @DisplayName("双端：发送后未读→送达→已读归零")
    void readAndDeliveryReceipt() {
        TestUser a = registerAndLogin("agsma");
        TestUser b = registerAndLogin("agsmb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        MessageVO sent = sendText(a, conv.getId(), "smoke-delivery-" + UUID.randomUUID());
        assertTrue(chatService.getUnreadCount(b.userId, conv.getId()) >= 1);

        ImWsFrame receipt = new ImWsFrame();
        receipt.setAction("deliveryReceipt");
        receipt.setServerMsgId(sent.getId());
        pushService.handleDeliveryReceipt(b.userId, receipt);

        ImMessage stored = messageMapper.selectOneById(sent.getId());
        assertNotNull(stored);
        assertEquals("delivered", stored.getDeliveryStatus());

        chatService.markAsRead(b.userId, conv.getId(), sent.getId());
        assertEquals(0, chatService.getUnreadCount(b.userId, conv.getId()));

        boolean listed = chatService.listConversations(b.userId).stream()
                .anyMatch(c -> conv.getId().equals(c.getId()) && c.getUnreadCount() != null && c.getUnreadCount() == 0);
        assertTrue(listed, "会话列表应包含该会话且未读为 0");
    }

    @Test
    @DisplayName("拉黑后双向不可发；取消拉黑后可发；会话仍可见")
    void blockUnblockPrivateChat() {
        TestUser a = registerAndLogin("agsba");
        TestUser b = registerAndLogin("agsbb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        friendService.blockFriend(a.userId, b.userId);
        assertTrue(friendService.isBlocked(a.userId, b.userId));

        boolean blockedListed = chatService.listConversations(a.userId).stream()
                .anyMatch(c -> conv.getId().equals(c.getId()) && Boolean.TRUE.equals(c.getBlocked()));
        assertTrue(blockedListed, "拉黑后会话应仍在列表且 blocked=true");

        assertThrows(CustomException.class, () -> sendText(a, conv.getId(), "blocked-from-a"));
        assertThrows(CustomException.class, () -> sendText(b, conv.getId(), "blocked-from-b"));

        friendService.unblockFriend(a.userId, b.userId);
        assertFalse(friendService.isBlocked(a.userId, b.userId));
        assertDoesNotThrow(() -> sendText(a, conv.getId(), "unblocked-ok"));
    }

    @Test
    @DisplayName("入群审批：申请→列表可见→审批通过后成为成员")
    void joinApprovalFlow() {
        TestUser owner = registerAndLogin("agsjo");
        TestUser member = registerAndLogin("agsjm");
        TestUser applicant = registerAndLogin("agsja");
        becomeFriends(owner, member);

        CreateGroupDTO dto = new CreateGroupDTO();
        dto.setName("审批冒烟群");
        dto.setMemberIds(List.of(member.userId));
        GroupConversationVO group = groupService.createGroup(owner.userId, dto);
        Long cid = group.getId();

        groupService.setJoinApproval(owner.userId, cid, true);
        groupService.requestJoin(applicant.userId, cid, "请通过");

        List<GroupJoinRequestVO> pending = groupService.listJoinRequests(owner.userId, cid);
        assertTrue(pending.stream().anyMatch(r -> applicant.userId == r.getApplicantId()));

        groupService.handleJoinRequest(owner.userId, cid, applicant.userId, true);

        boolean joined = groupService.listMembers(owner.userId, cid).stream()
                .anyMatch(m -> applicant.userId == m.getUserId());
        assertTrue(joined, "审批通过后申请人应在群成员中");
        assertTrue(groupService.listJoinRequests(owner.userId, cid).isEmpty());
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        becomeFriends(a, b);
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }

    private void becomeFriends(TestUser a, TestUser b) {
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
        msg.setClientMsgId(UUID.randomUUID().toString());
        return chatService.sendMessage(sender.userId, msg);
    }
}
