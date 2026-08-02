package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.PresenceService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Mock
    private PresenceService presenceService;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ImMessagePushService pushService;

    /** 同步执行的 ExecutorService，满足生产代码对 ExecutorService 的强转。 */
    private static ExecutorService directExecutorService() {
        return new AbstractExecutorService() {
            private final AtomicBoolean shutdown = new AtomicBoolean();

            @Override
            public void shutdown() {
                shutdown.set(true);
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown.set(true);
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown.get();
            }

            @Override
            public boolean isTerminated() {
                return shutdown.get();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(presenceService.getInstanceId()).thenReturn("test-instance");
        org.mockito.Mockito.lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
        ExecutorService direct = directExecutorService();
        pushService = new ImMessagePushService(
                chatService, memberMapper, messageMapper, sysUserMapper,
                channelManager, objectMapper, direct, direct, redisTemplate, messageStormService,
                presenceService, new com.linkx.server.config.LinkxProperties());
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

    @Test
    @DisplayName("pushToUser / pushToUserLocal / deliverLocal")
    void pushToUserPaths() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(channel);
        when(channelManager.getChannels(1L)).thenReturn(group);

        pushService.pushToUser(1L, "notification_refresh", Map.of("type", "x"));
        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertTrue(frame.text().contains("notification_refresh"));

        pushService.pushToUserLocal(1L, "ping", Map.of("a", 1));
        assertNotNull(channel.readOutbound());

        pushService.deliverLocal(1L, "{\"action\":\"custom\"}");
        TextWebSocketFrame custom = channel.readOutbound();
        assertEquals("{\"action\":\"custom\"}", custom.text());
    }

    @Test
    @DisplayName("pushActionToConversationMembers")
    void pushActionToMembers() {
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                com.linkx.server.entity.ImConversationMember.builder().userId(1L).build(),
                com.linkx.server.entity.ImConversationMember.builder().userId(2L).build()
        ));
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();
        DefaultChannelGroup g1 = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        DefaultChannelGroup g2 = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        g1.add(c1);
        g2.add(c2);
        when(channelManager.getChannels(1L)).thenReturn(g1);
        when(channelManager.getChannels(2L)).thenReturn(g2);

        pushService.pushActionToConversationMembers(10L, "group_mute_all_changed", Map.of("x", 1));
        assertNotNull(c1.readOutbound());
        assertNotNull(c2.readOutbound());
    }

    @Test
    @DisplayName("handleRetry 成功重发 dedup 消息")
    void handleRetry_success() throws Exception {
        when(messageStormService.checkAndRecordUserStorm(1L)).thenReturn(false);
        MessageVO sent = MessageVO.builder()
                .id(88L).conversationId(10L).senderId(1L).type("text").content("retry-me").build();
        when(chatService.sendMessage(eq(1L), any(SendMessageDTO.class))).thenReturn(sent);
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                ImConversationMember.builder().userId(1L).conversationId(10L).build()
        ));
        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        ImWsFrame retryReq = new ImWsFrame();
        retryReq.setConversationId("10");
        retryReq.setMsgType("text");
        retryReq.setContent("retry-me");
        retryReq.setClientMsgId("c-retry");
        pushService.handleRetry(1L, retryReq);

        TextWebSocketFrame frame = senderCh.readOutbound();
        assertNotNull(frame);
        ImWsFrame resp = objectMapper.readValue(frame.text(), ImWsFrame.class);
        assertEquals("ack", resp.getAction());
    }

    @Test
    @DisplayName("handleRecall / handleEdit 异常回错误帧")
    void recallEditErrors() throws Exception {
        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        when(chatService.recallMessage(1L, 10L, 5L)).thenThrow(new CustomException(403, "无权"));
        ImWsFrame recallReq = new ImWsFrame();
        recallReq.setConversationId("10");
        recallReq.setServerMsgId(5L);
        pushService.handleRecall(1L, recallReq);
        TextWebSocketFrame err = senderCh.readOutbound();
        ImWsFrame errFrame = objectMapper.readValue(err.text(), ImWsFrame.class);
        assertEquals(403, errFrame.getCode());

        when(chatService.editMessage(1L, 10L, 6L, "new")).thenThrow(new CustomException(400, "bad"));
        ImWsFrame editReq = new ImWsFrame();
        editReq.setConversationId("10");
        editReq.setServerMsgId(6L);
        editReq.setContent("new");
        pushService.handleEdit(1L, editReq);
        assertNotNull(senderCh.readOutbound());
    }

    @Test
    @DisplayName("handleDeliveryReceipt 非成员拒绝")
    void handleDeliveryReceiptForbidden() throws Exception {
        when(messageMapper.selectOneById(7L)).thenReturn(
                ImMessage.builder().id(7L).conversationId(10L).senderId(2L).build()
        );
        when(memberMapper.selectCountByQuery(any())).thenReturn(0L);
        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        ImWsFrame receiptReq = new ImWsFrame();
        receiptReq.setServerMsgId(7L);
        pushService.handleDeliveryReceipt(1L, receiptReq);

        TextWebSocketFrame err = senderCh.readOutbound();
        ImWsFrame errFrame = objectMapper.readValue(err.text(), ImWsFrame.class);
        assertEquals(403, errFrame.getCode());
    }

    @Test
    @DisplayName("pushToUser 离线走集群扇出")
    void pushToUserOfflineCluster() {
        when(channelManager.getChannels(99L)).thenReturn(null);
        pushService.pushToUser(99L, "ping", Map.of("x", 1));
        verify(streamOps, atLeastOnce()).add(any(org.springframework.data.redis.connection.stream.MapRecord.class));
    }

    @Test
    @DisplayName("sendError / detectMessageStorm / cache")
    void errorStormCache() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        pushService.sendError(channel, 400, "bad", "c1");
        TextWebSocketFrame err = channel.readOutbound();
        ImWsFrame frame = objectMapper.readValue(err.text(), ImWsFrame.class);
        assertEquals(400, frame.getCode());

        when(messageStormService.checkAndRecordUserStorm(1L)).thenReturn(true);
        assertTrue(pushService.detectMessageStorm(1L));
        when(listOps.range(startsWith("linkx:msg:storm:log:"), eq(0L), eq(-1L))).thenReturn(List.of("log"));
        assertEquals(1, pushService.getStormLogs(1L).size());

        MessageVO vo = MessageVO.builder().id(1L).conversationId(2L).type("text").content("h").build();
        when(listOps.rightPush(anyString(), anyString())).thenReturn(1L);
        pushService.cacheRecentMessage(2L, vo);
        verify(listOps).rightPush(eq("linkx:msg:cache:2"), anyString());
        verify(listOps).trim(eq("linkx:msg:cache:2"), eq(-50L), eq(-1L));
        verify(redisTemplate).expire(eq("linkx:msg:cache:2"), any());
    }

    @Test
    @DisplayName("handleSend 成功发送并 pushToConversationMembers")
    void handleSend_success() throws Exception {
        when(messageStormService.checkAndRecordUserStorm(1L)).thenReturn(false);
        MessageVO sent = MessageVO.builder()
                .id(99L).conversationId(10L).senderId(1L).type("text").content("hi").build();
        when(chatService.sendMessage(eq(1L), any(SendMessageDTO.class))).thenReturn(sent);
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                ImConversationMember.builder().userId(1L).conversationId(10L).build(),
                ImConversationMember.builder().userId(2L).conversationId(10L).build()
        ));
        when(presenceService.isOnline(2L)).thenReturn(true);
        when(messageMapper.selectOneById(99L)).thenReturn(
                ImMessage.builder().id(99L).deliveryStatus("sent").build()
        );

        EmbeddedChannel senderCh = new EmbeddedChannel();
        EmbeddedChannel recvCh = new EmbeddedChannel();
        DefaultChannelGroup senderGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        DefaultChannelGroup recvGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        senderGroup.add(senderCh);
        recvGroup.add(recvCh);
        when(channelManager.getChannels(1L)).thenReturn(senderGroup);
        when(channelManager.getChannels(2L)).thenReturn(recvGroup);

        ImWsFrame frame = new ImWsFrame();
        frame.setConversationId("10");
        frame.setMsgType("text");
        frame.setContent("hi");
        frame.setClientMsgId("c-send-1");

        pushService.handleSend(1L, frame);

        TextWebSocketFrame ack = senderCh.readOutbound();
        assertNotNull(ack);
        ImWsFrame ackFrame = objectMapper.readValue(ack.text(), ImWsFrame.class);
        assertEquals("ack", ackFrame.getAction());
        assertEquals("c-send-1", ackFrame.getClientMsgId());

        TextWebSocketFrame msg = recvCh.readOutbound();
        assertNotNull(msg);
        assertTrue(msg.text().contains("\"action\":\"message\""));
        verify(streamOps, atLeastOnce()).add(any(org.springframework.data.redis.connection.stream.MapRecord.class));
    }

    @Test
    @DisplayName("handleSend 风暴检测拒绝")
    void handleSend_stormRejected() throws Exception {
        when(messageStormService.checkAndRecordUserStorm(1L)).thenReturn(true);
        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        ImWsFrame frame = new ImWsFrame();
        frame.setConversationId("10");
        frame.setMsgType("text");
        frame.setContent("spam");
        frame.setClientMsgId("c-storm");

        pushService.handleSend(1L, frame);

        TextWebSocketFrame err = senderCh.readOutbound();
        ImWsFrame resp = objectMapper.readValue(err.text(), ImWsFrame.class);
        assertEquals("error", resp.getAction());
        assertEquals(429, resp.getCode());
        verify(chatService, never()).sendMessage(anyLong(), any());
    }

    @Test
    @DisplayName("handleSend 线程池饱和")
    void handleSend_executorRejected() throws Exception {
        ExecutorService rejecting = new AbstractExecutorService() {
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
            @Override public void execute(Runnable r) { throw new RejectedExecutionException(); }
            @Override public <T> Future<T> submit(Callable<T> task) { throw new RejectedExecutionException(); }
            @Override public Future<?> submit(Runnable task) { throw new RejectedExecutionException(); }
        };
        pushService = new ImMessagePushService(
                chatService, memberMapper, messageMapper, sysUserMapper,
                channelManager, objectMapper, rejecting, rejecting, redisTemplate,
                messageStormService, presenceService, new com.linkx.server.config.LinkxProperties());

        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        ImWsFrame frame = new ImWsFrame();
        frame.setConversationId("10");
        frame.setMsgType("text");
        frame.setContent("busy");
        pushService.handleSend(1L, frame);

        TextWebSocketFrame err = senderCh.readOutbound();
        ImWsFrame resp = objectMapper.readValue(err.text(), ImWsFrame.class);
        assertEquals(503, resp.getCode());
    }

    @Test
    @DisplayName("pushToAllOnline / deliverLocalBroadcast 集群广播")
    void pushToAllOnlineAndBroadcast() {
        EmbeddedChannel ch = new EmbeddedChannel();
        DefaultChannelGroup all = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        all.add(ch);
        doAnswer(inv -> {
            String json = inv.getArgument(0);
            all.writeAndFlush(new TextWebSocketFrame(json));
            return null;
        }).when(channelManager).deliverToAllLocal(anyString());

        pushService.pushToAllOnline("announcement", Map.of("title", "hi"));
        assertNotNull(ch.readOutbound());
        verify(streamOps, atLeastOnce()).add(any(org.springframework.data.redis.connection.stream.MapRecord.class));

        pushService.deliverLocalBroadcast("{\"action\":\"announcement\"}");
        assertNotNull(ch.readOutbound());
    }

    @Test
    @DisplayName("handleSync 离线消息补推")
    void handleSync_withOfflineMessages() throws Exception {
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                ImConversationMember.builder().userId(1L).conversationId(10L).build()
        ));
        Date now = new Date();
        ImMessage offline = ImMessage.builder()
                .id(200L).conversationId(10L).senderId(2L)
                .type(ImMessage.TYPE_TEXT).content("offline-msg").createTime(now).build();
        when(messageMapper.selectListByQuery(any())).thenReturn(new ArrayList<>(List.of(offline)));
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.of(
                SysUser.builder().id(2L).nickname("Bob").avatar("av2").build()
        ));

        EmbeddedChannel channel = new EmbeddedChannel();
        ImWsFrame req = new ImWsFrame();
        req.setServerMsgId(100L);
        pushService.handleSync(1L, req, channel);

        TextWebSocketFrame msgFrame = channel.readOutbound();
        assertNotNull(msgFrame);
        assertTrue(msgFrame.text().contains("offline-msg"));

        TextWebSocketFrame done = channel.readOutbound();
        ImWsFrame doneFrame = objectMapper.readValue(done.text(), ImWsFrame.class);
        assertEquals("syncDone", doneFrame.getAction());
        assertEquals(200, doneFrame.getCode());
    }

    @Test
    @DisplayName("handleRecall / handleEdit / handleRetry / handleDeliveryReceipt")
    void wsActionHandlers() throws Exception {
        EmbeddedChannel senderCh = new EmbeddedChannel();
        DefaultChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.add(senderCh);
        when(channelManager.getChannels(1L)).thenReturn(group);

        MessageVO recalled = MessageVO.builder().id(5L).conversationId(10L).senderId(1L).type("recall").build();
        when(chatService.recallMessage(1L, 10L, 5L)).thenReturn(recalled);
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                ImConversationMember.builder().userId(1L).conversationId(10L).build()
        ));

        ImWsFrame recallReq = new ImWsFrame();
        recallReq.setConversationId("10");
        recallReq.setServerMsgId(5L);
        pushService.handleRecall(1L, recallReq);
        assertNotNull(senderCh.readOutbound());

        MessageVO edited = MessageVO.builder().id(6L).conversationId(10L).senderId(1L).type("text").content("new").build();
        when(chatService.editMessage(1L, 10L, 6L, "new")).thenReturn(edited);
        ImWsFrame editReq = new ImWsFrame();
        editReq.setConversationId("10");
        editReq.setServerMsgId(6L);
        editReq.setContent("new");
        pushService.handleEdit(1L, editReq);
        assertNotNull(senderCh.readOutbound());

        EmbeddedChannel retryCh = new EmbeddedChannel();
        DefaultChannelGroup retryGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        retryGroup.add(retryCh);
        when(channelManager.getChannels(1L)).thenReturn(retryGroup);

        ImWsFrame retryReq = new ImWsFrame();
        retryReq.setClientMsgId("");
        pushService.handleRetry(1L, retryReq);
        TextWebSocketFrame retryErr = retryCh.readOutbound();
        ImWsFrame retryFrame = objectMapper.readValue(retryErr.text(), ImWsFrame.class);
        assertEquals(400, retryFrame.getCode());

        when(messageMapper.selectOneById(7L)).thenReturn(
                ImMessage.builder().id(7L).conversationId(10L).senderId(2L).build()
        );
        when(memberMapper.selectCountByQuery(any())).thenReturn(1L);
        EmbeddedChannel sender2Ch = new EmbeddedChannel();
        DefaultChannelGroup sender2Group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        sender2Group.add(sender2Ch);
        when(channelManager.getChannels(2L)).thenReturn(sender2Group);

        ImWsFrame receiptReq = new ImWsFrame();
        receiptReq.setServerMsgId(7L);
        pushService.handleDeliveryReceipt(1L, receiptReq);
        assertNotNull(sender2Ch.readOutbound());
    }

    @Test
    @DisplayName("pushReadReceipt / pushRecall / pushEdit / getMessageReadCount")
    void pushReceiptsAndReadCount() {
        when(memberMapper.selectListByQuery(any())).thenReturn(List.of(
                ImConversationMember.builder().userId(1L).conversationId(10L).build(),
                ImConversationMember.builder().userId(2L).conversationId(10L).build()
        ));
        EmbeddedChannel ch2 = new EmbeddedChannel();
        DefaultChannelGroup g2 = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        g2.add(ch2);
        when(channelManager.getChannels(2L)).thenReturn(g2);

        pushService.pushReadReceipt(10L, 1L, 50L);
        assertNotNull(ch2.readOutbound());

        MessageVO msg = MessageVO.builder().id(1L).conversationId(10L).senderId(1L).type("text").content("x").build();
        EmbeddedChannel ch1 = new EmbeddedChannel();
        DefaultChannelGroup g1 = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        g1.add(ch1);
        when(channelManager.getChannels(1L)).thenReturn(g1);

        pushService.pushRecallToConversationMembers(msg);
        assertNotNull(ch1.readOutbound());
        pushService.pushEditToConversationMembers(msg);
        assertNotNull(ch1.readOutbound());

        when(memberMapper.selectCountByQuery(any())).thenReturn(3L);
        assertEquals(3L, pushService.getMessageReadCount(10L, 1L, 5));
    }

    @Test
    @DisplayName("getCachedMessages 过滤无效 JSON / getStormLogs 空列表")
    void cacheAndStormLogs() {
        when(listOps.range(eq("linkx:msg:cache:10"), eq(0L), eq(-1L))).thenReturn(null);
        assertTrue(pushService.getCachedMessages(10L).isEmpty());

        when(listOps.range(eq("linkx:msg:cache:11"), eq(0L), eq(-1L))).thenReturn(List.of("{invalid"));
        assertTrue(pushService.getCachedMessages(11L).isEmpty());

        when(listOps.range(startsWith("linkx:msg:storm:log:"), eq(0L), eq(-1L))).thenReturn(null);
        assertTrue(pushService.getStormLogs(1L).isEmpty());
    }

    @Test
    @DisplayName("sendError 非活跃 channel 跳过")
    void sendError_inactiveChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.close();
        pushService.sendError(channel, 500, "fail", "cid");
        assertNull(channel.readOutbound());
    }
}
