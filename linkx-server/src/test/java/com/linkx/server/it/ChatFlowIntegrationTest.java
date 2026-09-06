package com.linkx.server.it;

import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 聊天核心链路集成测试：建会话 → 发消息 → 落库密文校验 → 拉历史 → 撤回 → 越权拒绝。
 */
class ChatFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private ImMessageMapper imMessageMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REGISTER_CODE = "886688";

    private Long register(String username) {
        String email = username + "@linkx-it.local";
        stringRedisTemplate.opsForValue().set(
                "linkx:register-email:" + email, REGISTER_CODE, Duration.ofMinutes(10));
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(TEST_PASSWORD);
        dto.setNickname("测试-" + username);
        dto.setEmail(email);
        dto.setEmailCode(REGISTER_CODE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        sysUserService.register(dto, request);
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(TEST_PASSWORD);
        TokenVO tokenVO = sysUserService.login(loginDTO, "127.0.0.1", "LinkX-IT/1.0", request);
        return tokenVO.getUser().getId();
    }

    /** 注册两个用户并结为好友，返回 [userA, userB] */
    private Long[] friends() {
        String a = uniqueName("cha");
        String b = uniqueName("chb");
        Long userA = register(a);
        Long userB = register(b);
        SendFriendRequestDTO sendDTO = new SendFriendRequestDTO();
        sendDTO.setUsername(b);
        friendService.sendFriendRequest(userA, sendDTO);
        Long requestId = friendService.listIncomingRequests(userB).stream()
                .filter(r -> r.getFromUserId().equals(userA)).findFirst().orElseThrow().getId();
        friendService.acceptFriendRequest(userB, requestId);
        return new Long[]{userA, userB};
    }

    @Test
    void sendMessage_persistedEncrypted_andReadable() {
        Long[] users = friends();
        Long userA = users[0];
        Long userB = users[1];

        ConversationVO conversation = chatService.getOrCreatePrivateConversation(userA, userB);
        assertThat(conversation.getId()).isNotNull();

        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(conversation.getId());
        dto.setMsgType("text");
        dto.setContent("机密消息：你好，LinkX 集成测试");
        dto.setClientMsgId("it-" + System.nanoTime());
        MessageVO sent = chatService.sendMessage(userA, dto);
        assertThat(sent.getId()).isNotNull();
        assertThat(sent.getContent()).isEqualTo("机密消息：你好，LinkX 集成测试");
        assertThat(sent.getSenderId()).isEqualTo(userA);

        // 落库应为密文（lxenc:v1 前缀 + content_enc_version=1），明文不落盘
        ImMessage row = imMessageMapper.selectOneById(sent.getId());
        assertThat(row.getContentEncVersion()).isEqualTo((byte) 1);
        assertThat(row.getContent()).startsWith("lxenc:v1:");
        assertThat(row.getContent()).doesNotContain("机密消息");

        // 拉历史时对会话成员解密还原
        List<MessageVO> history = chatService.listMessages(userB, conversation.getId(), null, null, 50);
        assertThat(history).extracting(MessageVO::getId).contains(sent.getId());
        MessageVO read = history.stream().filter(m -> m.getId().equals(sent.getId()))
                .findFirst().orElseThrow();
        assertThat(read.getContent()).isEqualTo("机密消息：你好，LinkX 集成测试");
    }

    @Test
    void sender_canRecallOwnMessage() {
        Long[] users = friends();
        Long userA = users[0];
        Long userB = users[1];

        ConversationVO conversation = chatService.getOrCreatePrivateConversation(userA, userB);
        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(conversation.getId());
        dto.setMsgType("text");
        dto.setContent("将被撤回的消息");
        dto.setClientMsgId("it-" + System.nanoTime());
        MessageVO sent = chatService.sendMessage(userA, dto);

        MessageVO recalled = chatService.recallMessage(userA, conversation.getId(), sent.getId());
        assertThat(recalled.getType()).isEqualTo("recall");

        // 非发送者不能撤回
        SendMessageDTO dto2 = new SendMessageDTO();
        dto2.setConversationId(conversation.getId());
        dto2.setMsgType("text");
        dto2.setContent("另一条消息");
        dto2.setClientMsgId("it-" + System.nanoTime());
        MessageVO sent2 = chatService.sendMessage(userB, dto2);
        assertThatThrownBy(() -> chatService.recallMessage(userA, conversation.getId(), sent2.getId()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void blockedFriend_cannotSend() {
        Long[] users = friends();
        Long userA = users[0];
        Long userB = users[1];

        ConversationVO conversation = chatService.getOrCreatePrivateConversation(userA, userB);

        friendService.blockFriend(userB, userA);
        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(conversation.getId());
        dto.setMsgType("text");
        dto.setContent("被屏蔽后的消息");
        dto.setClientMsgId("it-" + System.nanoTime());
        assertThatThrownBy(() -> chatService.sendMessage(userB, dto))
                .isInstanceOf(CustomException.class);
    }
}
