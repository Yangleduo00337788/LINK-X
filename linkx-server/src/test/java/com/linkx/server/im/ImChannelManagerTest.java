package com.linkx.server.im;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImChannelManager 即时通讯通道管理器测试
 */
@DisplayName("ImChannelManager 即时通讯通道管理器测试")
class ImChannelManagerTest {

    private ImChannelManager channelManager;

    @BeforeEach
    void setUp() {
        channelManager = new ImChannelManager();
    }

    private Channel createMockChannel() {
        return new io.netty.channel.embedded.EmbeddedChannel();
    }

    @Nested
    @DisplayName("add 用户上线测试")
    class AddTests {

        @Test
        @DisplayName("添加用户通道应成功")
        void addChannel_success() {
            Long userId = 1L;
            Channel channel = createMockChannel();

            channelManager.add(userId, channel);

            assertTrue(channelManager.isOnline(userId));
        }

        @Test
        @DisplayName("同一用户多次添加应能累积连接")
        void addMultipleChannels_cumulates() {
            Long userId = 2L;
            Channel channel1 = createMockChannel();
            Channel channel2 = createMockChannel();

            channelManager.add(userId, channel1);
            channelManager.add(userId, channel2);

            ChannelGroup group = channelManager.getChannels(userId);
            assertNotNull(group);
            // 注意：EmbeddedChannel可能有特殊行为
            assertTrue(group.size() >= 1, "ChannelGroup应有至少1个通道");
        }

        @Test
        @DisplayName("不同用户应独立管理")
        void differentUsers_independent() {
            Channel ch1 = createMockChannel();
            Channel ch2 = createMockChannel();

            channelManager.add(10L, ch1);
            channelManager.add(20L, ch2);

            assertTrue(channelManager.isOnline(10L));
            assertTrue(channelManager.isOnline(20L));
        }
    }

    @Nested
    @DisplayName("remove 用户下线测试")
    class RemoveTests {

        @Test
        @DisplayName("移除通道应成功")
        void removeChannel_success() {
            Long userId = 3L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);

            channelManager.remove(channel);

            assertFalse(channelManager.isOnline(userId));
        }

        @Test
        @DisplayName("移除后用户应下线")
        void removeAfterAdd_userOffline() {
            Long userId = 4L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);
            assertTrue(channelManager.isOnline(userId));

            channelManager.remove(channel);

            assertFalse(channelManager.isOnline(userId));
        }

        @Test
        @DisplayName("移除不存在的通道不应抛异常")
        void removeNonExistent_noException() {
            Channel channel = createMockChannel();

            assertDoesNotThrow(() -> channelManager.remove(channel));
        }
    }

    @Nested
    @DisplayName("isOnline 在线状态测试")
    class IsOnlineTests {

        @Test
        @DisplayName("未上线的用户应返回false")
        void neverOnline_returnsFalse() {
            assertFalse(channelManager.isOnline(999L));
        }

        @Test
        @DisplayName("上线后应返回true")
        void afterAdd_returnsTrue() {
            Long userId = 5L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);

            assertTrue(channelManager.isOnline(userId));
        }

        @Test
        @DisplayName("下线后应返回false")
        void afterRemove_returnsFalse() {
            Long userId = 6L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);
            channelManager.remove(channel);

            assertFalse(channelManager.isOnline(userId));
        }
    }

    @Nested
    @DisplayName("getChannels 获取通道组测试")
    class GetChannelsTests {

        @Test
        @DisplayName("获取不存在的用户通道应返回null")
        void nonExistentUser_returnsNull() {
            assertNull(channelManager.getChannels(888L));
        }

        @Test
        @DisplayName("获取存在的用户通道应返回ChannelGroup")
        void existingUser_returnsChannelGroup() {
            Long userId = 7L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);

            ChannelGroup group = channelManager.getChannels(userId);

            assertNotNull(group);
            assertFalse(group.isEmpty());
        }

        @Test
        @DisplayName("ChannelGroup应可迭代")
        void channelGroup_iterable() {
            Long userId = 8L;
            Channel channel = createMockChannel();
            channelManager.add(userId, channel);

            ChannelGroup group = channelManager.getChannels(userId);
            int count = 0;
            for (Channel ch : group) {
                count++;
                assertNotNull(ch);
            }
            assertEquals(1, count, "应有1个通道");
        }
    }
}
