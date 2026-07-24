package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MessageStormService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImMessagePushService 消息推送测试")
class ImMessagePushServiceTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ImConversationMemberMapper memberMapper;
    @Mock
    private ImMessageMapper messageMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private ImChannelManager channelManager;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private MessageStormService messageStormService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ImMessagePushService pushService;

    @BeforeEach
    void setUp() {
        pushService = new ImMessagePushService(
                chatService, memberMapper, messageMapper, sysUserMapper,
                channelManager, objectMapper, Runnable::run, redisTemplate, messageStormService);
    }

    @Test
    @DisplayName("构建 pong 帧应成功")
    void buildPong_success() throws Exception {
        ImWsFrame frame = objectMapper.readValue(pushService.buildPong(), ImWsFrame.class);
        assertEquals("pong", frame.getAction());
    }

    @Test
    @DisplayName("sync 动作在无会话时应返回完成帧")
    void handleSync_success() throws Exception {
        when(memberMapper.selectListByQuery(any())).thenReturn(Collections.emptyList());

        EmbeddedChannel channel = new EmbeddedChannel();
        ImWsFrame req = new ImWsFrame();
        req.setClientMsgId("c1");
        req.setServerMsgId(99L);

        pushService.handleSync(1L, req, channel);

        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        ImWsFrame resp = objectMapper.readValue(frame.text(), ImWsFrame.class);
        assertEquals("sync", resp.getAction());
        assertEquals(200, resp.getCode());
        assertEquals(1, ((Map<?, ?>) resp.getData()).get("userId"));
    }

    @Test
    @DisplayName("ack 帧应包含 serverMsgId")
    void sendAck_success() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        MessageVO vo = MessageVO.builder()
                .id(11L)
                .conversationId(22L)
                .senderId(1L)
                .type("text")
                .content("hello")
                .redPacketTotalAmount(BigDecimal.ONE)
                .build();

        pushService.sendAck(channel, vo, "client-1");

        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        ImWsFrame resp = objectMapper.readValue(frame.text(), ImWsFrame.class);
        assertEquals("ack", resp.getAction());
        assertEquals("client-1", resp.getClientMsgId());
        assertEquals(11L, resp.getServerMsgId());
    }
}
