package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MessageStormService;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IM 主链路（WebSocket 通道侧）端到端单元测试。
 * <p>
 * 真实 Netty WS Server 不易在单测中拉起，这里用 EmbeddedChannel + ImChannelManager
 * 覆盖上线、推送、错误帧与下线主路径。HTTP 侧好友/私聊主链路见
 * {@code com.linkx.server.service.ImMainLinkE2ETest}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IM 主链路 WebSocket 侧 E2E")
class ImMainLinkE2ETest {

    @Mock
    private ChatService chatService;
    @Mock
    private ImConversationMemberMapper memberMapper;
    @Mock
    private ImMessageMapper messageMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private MessageStormService messageStormService;

    private ImChannelManager channelManager;
    private ImMessagePushService pushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        channelManager = new ImChannelManager();
        pushService = new ImMessagePushService(
                chatService, memberMapper, messageMapper, sysUserMapper,
                channelManager, objectMapper, Runnable::run, redisTemplate, messageStormService);
    }

    @Nested
    @DisplayName("上线-推送-下线主路径")
    class OnlinePushOffline {

        @Test
        @DisplayName("用户上线后应能收到 pushToUser 推送帧")
        void onlineThenPush_shouldDeliverFrame() throws Exception {
            Long userId = 1001L;
            EmbeddedChannel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);
            assertTrue(channelManager.isOnline(userId));

            pushService.pushToUser(userId, "notify", java.util.Map.of("hello", "world"));

            TextWebSocketFrame outbound = channel.readOutbound();
            assertNotNull(outbound, "在线用户应收到推送");
            ImWsFrame frame = objectMapper.readValue(outbound.text(), ImWsFrame.class);
            assertEquals("notify", frame.getAction());
            assertNotNull(frame.getData());
        }

        @Test
        @DisplayName("下线后 pushToUser 不应再投递")
        void offlineThenPush_shouldNotDeliver() {
            Long userId = 1002L;
            EmbeddedChannel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);
            channelManager.remove(channel);
            assertFalse(channelManager.isOnline(userId));

            assertDoesNotThrow(() -> pushService.pushToUser(userId, "notify", "x"));
            assertNull(channel.readOutbound());
        }

        @Test
        @DisplayName("双端注册后至少一端可收到推送")
        void multiDevice_atLeastOneReceives() {
            // EmbeddedChannel + DefaultChannelGroup 可能只保留 1 个连接（见 ImChannelManagerTest 注释）
            Long userId = 1003L;
            EmbeddedChannel ch1 = new EmbeddedChannel();
            EmbeddedChannel ch2 = new EmbeddedChannel();
            channelManager.add(userId, ch1);
            channelManager.add(userId, ch2);
            assertTrue(channelManager.isOnline(userId));

            pushService.pushToUser(userId, "ping", "payload");

            boolean delivered = ch1.readOutbound() != null || ch2.readOutbound() != null;
            assertTrue(delivered, "至少一端应收到推送");
        }
    }

    @Nested
    @DisplayName("心跳与错误帧")
    class HeartbeatAndError {

        @Test
        @DisplayName("buildPong 应返回合法 pong 帧")
        void buildPong_ok() throws Exception {
            ImWsFrame frame = objectMapper.readValue(pushService.buildPong(), ImWsFrame.class);
            assertEquals("pong", frame.getAction());
        }

        @Test
        @DisplayName("sendError 应写出 error 动作帧")
        void sendError_writesErrorFrame() throws Exception {
            EmbeddedChannel channel = new EmbeddedChannel();
            pushService.sendError(channel, 401, "未授权");

            TextWebSocketFrame outbound = channel.readOutbound();
            assertNotNull(outbound);
            ImWsFrame frame = objectMapper.readValue(outbound.text(), ImWsFrame.class);
            assertEquals("error", frame.getAction());
            assertEquals(401, frame.getCode());
            assertEquals("未授权", frame.getMessage());
        }
    }

    @Nested
    @DisplayName("通道生命周期")
    class ChannelLifecycle {

        @Test
        @DisplayName("上线后移除通道应下线")
        void addThenRemove_offline() {
            Long userId = 1004L;
            Channel ch = new EmbeddedChannel();
            channelManager.add(userId, ch);
            assertTrue(channelManager.isOnline(userId));

            channelManager.remove(ch);
            assertFalse(channelManager.isOnline(userId));
        }
    }
}
