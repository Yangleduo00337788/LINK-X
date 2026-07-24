package com.linkx.server.service;

import com.linkx.server.controller.dto.CallCancelDTO;
import com.linkx.server.controller.dto.CallInviteDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendRedPacketDTO;
import com.linkx.server.controller.vo.CallInviteVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.RedPacketVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("通话与红包高风险路径")
class CallAndRedPacketFlowTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private CallService callService;
    @Autowired
    private RedPacketService redPacketService;
    @Autowired
    private BalanceService balanceService;

    private ConversationVO becomeFriendsAndOpenChat(TestUser a, TestUser b) {
        SendFriendRequestDTO dto = new SendFriendRequestDTO();
        dto.setUsername(b.username);
        friendService.sendFriendRequest(a.userId, dto);
        friendService.acceptFriendRequest(b.userId, friendService.listIncomingRequests(b.userId).get(0).getId());
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }

    @Test
    @DisplayName("私聊通话 invite/cancel 与非法会话")
    void callInviteCancel() {
        TestUser a = registerAndLogin("calla");
        TestUser b = registerAndLogin("callb");
        ConversationVO conv = becomeFriendsAndOpenChat(a, b);

        CallInviteDTO invite = new CallInviteDTO();
        invite.setConversationId(conv.getId());
        invite.setCallType("voice");
        CallInviteVO vo = callService.invite(a.userId, invite);
        assertNotNull(vo.getCallId());

        CallCancelDTO cancel = new CallCancelDTO();
        cancel.setCallId(vo.getCallId());
        assertDoesNotThrow(() -> callService.cancel(a.userId, cancel));

        CallInviteDTO bad = new CallInviteDTO();
        bad.setConversationId(999999L);
        bad.setCallType("video");
        assertThrows(CustomException.class, () -> callService.invite(a.userId, bad));
    }

    @Test
    @DisplayName("发红包并可被对方领取，余额正确划转")
    void redPacketSendAndReceive() {
        TestUser a = registerAndLogin("rpa");
        TestUser b = registerAndLogin("rpb");
        ConversationVO conv = becomeFriendsAndOpenChat(a, b);

        balanceService.addBalance(a.userId, new BigDecimal("100.00"), "recharge", null, "test topup");
        BigDecimal senderBefore = balanceService.getBalance(a.userId).getBalance();
        BigDecimal receiverBefore = balanceService.getBalance(b.userId).getBalance();

        SendRedPacketDTO packet = new SendRedPacketDTO();
        packet.setConversationId(conv.getId());
        packet.setTotalAmount(new BigDecimal("1.00"));
        packet.setTotalCount(1);
        packet.setType("normal");
        packet.setGreeting("恭喜发财");

        RedPacketVO sent = redPacketService.sendRedPacket(a.userId, packet);
        assertNotNull(sent.getId());
        assertEquals("active", sent.getStatus());
        // 发送后：可用余额减少，冻结增加；总 balance 字段通常为可用余额
        assertEquals(0, senderBefore.subtract(new BigDecimal("1.00"))
                .compareTo(balanceService.getBalance(a.userId).getBalance()));

        RedPacketVO received = redPacketService.receiveRedPacket(b.userId, String.valueOf(sent.getId()));
        assertNotNull(received);
        assertTrue(Boolean.TRUE.equals(received.getReceived())
                || "finished".equals(received.getStatus())
                || received.getReceivedAmount() != null);

        BigDecimal senderAfter = balanceService.getBalance(a.userId).getBalance();
        BigDecimal receiverAfter = balanceService.getBalance(b.userId).getBalance();
        assertEquals(0, senderBefore.subtract(new BigDecimal("1.00")).compareTo(senderAfter),
                "发送方可用余额应减少 1.00");
        assertEquals(0, receiverBefore.add(new BigDecimal("1.00")).compareTo(receiverAfter),
                "领取方可用余额应增加 1.00");

        assertThrows(CustomException.class,
                () -> redPacketService.receiveRedPacket(b.userId, String.valueOf(sent.getId())),
                "同一红包不可重复领取");
    }

    @Test
    @DisplayName("余额不足时不可发送红包")
    void redPacketSend_insufficientBalance_rejected() {
        TestUser a = registerAndLogin("rpshort");
        TestUser b = registerAndLogin("rppeer");
        ConversationVO conv = becomeFriendsAndOpenChat(a, b);

        // 仅充值 0.50，尝试发 1.00
        balanceService.addBalance(a.userId, new BigDecimal("0.50"), "recharge", null, "partial");
        BigDecimal before = balanceService.getBalance(a.userId).getBalance();

        SendRedPacketDTO packet = new SendRedPacketDTO();
        packet.setConversationId(conv.getId());
        packet.setTotalAmount(new BigDecimal("1.00"));
        packet.setTotalCount(1);
        packet.setType("normal");
        packet.setGreeting("不够也想发");

        CustomException ex = assertThrows(CustomException.class,
                () -> redPacketService.sendRedPacket(a.userId, packet));
        assertTrue(ex.getMessage().contains("余额") || ex.getCode() == 400);

        assertEquals(0, before.compareTo(balanceService.getBalance(a.userId).getBalance()),
                "余额不足发送失败后可用余额不应变化");
        assertEquals(0, BigDecimal.ZERO.compareTo(
                Optional.ofNullable(balanceService.getBalance(a.userId).getFrozen()).orElse(BigDecimal.ZERO)),
                "余额不足发送失败后不应产生冻结");
    }

    @Test
    @DisplayName("非会话成员不可领取红包")
    void redPacketReceive_outsider_rejected() {
        TestUser a = registerAndLogin("rphost");
        TestUser b = registerAndLogin("rpmem");
        TestUser outsider = registerAndLogin("rpout");
        ConversationVO conv = becomeFriendsAndOpenChat(a, b);

        balanceService.addBalance(a.userId, new BigDecimal("10.00"), "recharge", null, "topup");
        SendRedPacketDTO packet = new SendRedPacketDTO();
        packet.setConversationId(conv.getId());
        packet.setTotalAmount(new BigDecimal("1.00"));
        packet.setTotalCount(1);
        packet.setType("normal");
        RedPacketVO sent = redPacketService.sendRedPacket(a.userId, packet);

        assertThrows(CustomException.class,
                () -> redPacketService.receiveRedPacket(outsider.userId, String.valueOf(sent.getId())));
    }
}
