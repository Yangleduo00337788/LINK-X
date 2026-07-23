package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
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
 * WebSocket 异常路径单元测试（EmbeddedChannel，不拉起真实 Netty Server）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket 异常路径测试")
class WebSocketExceptionTest {

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
    @DisplayName("错误帧投递")
    class ErrorFrame {

        @Test
        @DisplayName("sendError 应对活跃通道写出 error 帧")
        void sendError_activeChannel() throws Exception {
            EmbeddedChannel channel = new EmbeddedChannel();
            pushService.sendError(channel, 400, "参数错误");

            TextWebSocketFrame frame = channel.readOutbound();
            assertNotNull(frame);
            ImWsFrame ws = objectMapper.readValue(frame.text(), ImWsFrame.class);
            assertEquals("error", ws.getAction());
            assertEquals(400, ws.getCode());
            assertEquals("参数错误", ws.getMessage());
        }

        @Test
        @DisplayName("sendError 对已关闭通道不应抛异常")
        void sendError_closedChannel_noThrow() {
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.close();
            assertDoesNotThrow(() -> pushService.sendError(channel, 500, "内部错误"));
        }
    }

    @Nested
    @DisplayName("离线与非法操作")
    class OfflineAndIllegal {

        @Test
        @DisplayName("向从未上线的用户推送不应抛异常")
        void pushToNeverOnline_noThrow() {
            assertDoesNotThrow(() -> pushService.pushToUser(99999L, "notify", "x"));
        }

        @Test
        @DisplayName("pushToUser 传入 null userId 应安全返回")
        void pushToNullUser_noThrow() {
            assertDoesNotThrow(() -> pushService.pushToUser(null, "notify", "x"));
        }

        @Test
        @DisplayName("移除从未注册的通道不应抛异常")
        void removeUnknownChannel_noThrow() {
            EmbeddedChannel channel = new EmbeddedChannel();
            assertDoesNotThrow(() -> channelManager.remove(channel));
            assertFalse(channelManager.isOnline(1L));
        }

        @Test
        @DisplayName("重复移除同一通道不应抛异常")
        void removeTwice_noThrow() {
            Long userId = 42L;
            EmbeddedChannel channel = new EmbeddedChannel();
            channelManager.add(userId, channel);
            channelManager.remove(channel);
            assertDoesNotThrow(() -> channelManager.remove(channel));
            assertFalse(channelManager.isOnline(userId));
        }
    }
}
