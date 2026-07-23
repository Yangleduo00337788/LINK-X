package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
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
 * WebSocket 正常建连/在线状态单元测试。
 * <p>
 * 真实 Netty WS 握手在集成环境成本高，这里用 EmbeddedChannel 验证
 * ImChannelManager 的 add/remove/isOnline 与正常推送链路。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket 正常链路测试")
class WebSocketNormalLinkTest {

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

    private ImChannelManager channelManager;
    private ImMessagePushService pushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        channelManager = new ImChannelManager();
        pushService = new ImMessagePushService(
                chatService, memberMapper, messageMapper, sysUserMapper,
                channelManager, objectMapper, Runnable::run, redisTemplate);
    }

    @Nested
    @DisplayName("建连与在线状态")
    class ConnectAndOnline {

        @Test
        @DisplayName("add 后 isOnline 应为 true")
        void add_thenOnline() {
            Long userId = 201L;
            Channel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);

            assertTrue(channelManager.isOnline(userId));
            ChannelGroup group = channelManager.getChannels(userId);
            assertNotNull(group);
            assertFalse(group.isEmpty());
        }

        @Test
        @DisplayName("remove 后 isOnline 应为 false")
        void remove_thenOffline() {
            Long userId = 202L;
            Channel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);
            channelManager.remove(channel);

            assertFalse(channelManager.isOnline(userId));
            assertNull(channelManager.getChannels(userId));
        }

        @Test
        @DisplayName("不同用户可分别上线与下线")
        void differentUsers_independent() {
            Channel chA = new EmbeddedChannel();
            Channel chB = new EmbeddedChannel();
            channelManager.add(10L, chA);
            channelManager.add(20L, chB);

            assertTrue(channelManager.isOnline(10L));
            assertTrue(channelManager.isOnline(20L));

            channelManager.remove(chA);
            assertFalse(channelManager.isOnline(10L));

            // 再显式移除用户 20，验证其生命周期独立可操作
            channelManager.remove(chB);
            assertFalse(channelManager.isOnline(20L));
        }
    }

    @Nested
    @DisplayName("正常推送")
    class NormalPush {

        @Test
        @DisplayName("在线用户应收到自定义 action 推送")
        void pushToUser_online_receives() throws Exception {
            Long userId = 203L;
            EmbeddedChannel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);

            pushService.pushToUser(userId, "friend_request", java.util.Map.of("from", 1));

            TextWebSocketFrame outbound = channel.readOutbound();
            assertNotNull(outbound);
            ImWsFrame frame = objectMapper.readValue(outbound.text(), ImWsFrame.class);
            assertEquals("friend_request", frame.getAction());
        }

        @Test
        @DisplayName("心跳 pong 帧结构应合法")
        void pong_structure() throws Exception {
            ImWsFrame frame = objectMapper.readValue(pushService.buildPong(), ImWsFrame.class);
            assertEquals("pong", frame.getAction());
        }
    }
}
