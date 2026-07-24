package com.linkx.server.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FriendService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 双端真实 WebSocket 冒烟：送达回执、已读回执、拉黑拦截。
 */
@DisplayName("双端联调冒烟（WS）")
class DualClientLiveSmokeTest extends BaseIntegrationTest {

    private static final int WS_PORT = freePort();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void enableWs(DynamicPropertyRegistry registry) {
        registry.add("linkx.im.websocket-port", () -> WS_PORT);
    }

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("双端：收消息发 deliveryReceipt，发送方收到回执；markAsRead 后收到 readReceipt")
    void deliveryAndReadReceipt_dualOnline() throws Exception {
        TestUser alice = registerAndLoginWithDevice("dcala", "dc-a-" + UUID.randomUUID());
        TestUser bob = registerAndLoginWithDevice("dcalb", "dc-b-" + UUID.randomUUID());
        ConversationVO conv = becomeFriendsAndOpen(alice, bob);
        String content = "dual-read-" + System.nanoTime();
        String clientMsgId = UUID.randomUUID().toString();

        try (WsSession aliceWs = connect(alice.accessToken, "dc-a-dev");
             WsSession bobWs = connect(bob.accessToken, "dc-b-dev")) {
            assertTrue(aliceWs.awaitOpen(5, TimeUnit.SECONDS));
            assertTrue(bobWs.awaitOpen(5, TimeUnit.SECONDS));

            aliceWs.send(String.format("""
                    {"action":"send","clientMsgId":"%s","conversationId":"%s","msgType":"text","content":"%s"}
                    """, clientMsgId, conv.getId(), content));

            JsonNode ack = aliceWs.awaitFrame(
                    f -> "ack".equals(text(f, "action")) && clientMsgId.equals(text(f, "clientMsgId")),
                    8, TimeUnit.SECONDS);
            assertNotNull(ack, "Alice 应收到 ack");
            long serverMsgId = ack.path("serverMsgId").asLong();
            assertTrue(serverMsgId > 0);

            JsonNode pushed = bobWs.awaitFrame(f ->
                    "message".equals(text(f, "action"))
                            && content.equals(f.path("data").path("content").asText()),
                    8, TimeUnit.SECONDS);
            assertNotNull(pushed, "Bob 应收到 message");

            // 在线投递可能已自动推 deliveryReceipt；Bob 再显式确认一次也安全
            bobWs.send(String.format("""
                    {"action":"deliveryReceipt","serverMsgId":%d}
                    """, serverMsgId));

            JsonNode delivery = aliceWs.awaitFrame(f ->
                    "deliveryReceipt".equals(text(f, "action"))
                            && serverMsgId == f.path("data").path("messageId").asLong(),
                    8, TimeUnit.SECONDS);
            assertNotNull(delivery, "Alice 应收到 deliveryReceipt");

            mockMvc.perform(post("/chat/sessions/{cid}/read", conv.getId())
                            .param("lastMessageId", String.valueOf(serverMsgId))
                            .header("Authorization", bob.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            JsonNode read = aliceWs.awaitFrame(f ->
                    "readReceipt".equals(text(f, "action"))
                            && bob.userId == f.path("data").path("readerId").asLong(),
                    8, TimeUnit.SECONDS);
            assertNotNull(read, "Alice 应收到 readReceipt");
            assertEquals(0, chatService.getUnreadCount(bob.userId, conv.getId()));
        }
    }

    @Test
    @DisplayName("双端：拉黑后发送方 WS 发送应失败（error），解除后可再发")
    void blockThenUnblock_wsSend() throws Exception {
        TestUser alice = registerAndLoginWithDevice("dcbla", "dc-ba-" + UUID.randomUUID());
        TestUser bob = registerAndLoginWithDevice("dcblb", "dc-bb-" + UUID.randomUUID());
        ConversationVO conv = becomeFriendsAndOpen(alice, bob);

        friendService.blockFriend(alice.userId, bob.userId);

        try (WsSession aliceWs = connect(alice.accessToken, "dc-ba-dev")) {
            assertTrue(aliceWs.awaitOpen(5, TimeUnit.SECONDS));
            String blockedId = UUID.randomUUID().toString();
            aliceWs.send(String.format("""
                    {"action":"send","clientMsgId":"%s","conversationId":"%s","msgType":"text","content":"should-fail"}
                    """, blockedId, conv.getId()));

            JsonNode err = aliceWs.awaitFrame(f ->
                    "error".equals(text(f, "action"))
                            || ("ack".equals(text(f, "action")) && blockedId.equals(text(f, "clientMsgId"))),
                    8, TimeUnit.SECONDS);
            assertNotNull(err);
            assertEquals("error", text(err, "action"), "拉黑后发送应返回 error 而非 ack");
        }

        friendService.unblockFriend(alice.userId, bob.userId);
        MessageVO sent = sendText(alice, conv.getId(), "after-unblock-" + UUID.randomUUID());
        assertNotNull(sent.getId());
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("dual-smoke");
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

    private static WsSession connect(String accessToken, String deviceId) throws Exception {
        awaitPortOpen(WS_PORT, 15_000);
        URI uri = URI.create("ws://127.0.0.1:" + WS_PORT + "/ws?token="
                + accessToken + "&deviceId=" + deviceId + "&deviceName=JUnit&deviceType=Test");
        WsSession session = new WsSession();
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, session)
                .get(8, TimeUnit.SECONDS);
        return session;
    }

    private static void awaitPortOpen(int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        fail("WebSocket 端口 " + port + " 在超时内未就绪");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText(null);
    }

    private static final class WsSession implements WebSocket.Listener, AutoCloseable {
        private final List<JsonNode> frames = new CopyOnWriteArrayList<>();
        private final AtomicReference<WebSocket> socket = new AtomicReference<>();
        private final CompletableFuture<Void> opened = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            socket.set(webSocket);
            opened.complete(null);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                try {
                    frames.add(MAPPER.readTree(partial.toString()));
                } catch (Exception ignored) {
                }
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            opened.completeExceptionally(new IllegalStateException("closed: " + statusCode + " " + reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            opened.completeExceptionally(error);
        }

        boolean awaitOpen(long timeout, TimeUnit unit) throws Exception {
            opened.get(timeout, unit);
            return true;
        }

        void send(String json) {
            WebSocket ws = socket.get();
            assertNotNull(ws);
            ws.sendText(json, true).join();
        }

        JsonNode awaitFrame(Predicate<JsonNode> matcher, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            while (System.currentTimeMillis() < deadline) {
                for (JsonNode frame : frames) {
                    if (matcher.test(frame)) {
                        return frame;
                    }
                }
                Thread.sleep(40);
            }
            return null;
        }

        @Override
        public void close() {
            WebSocket ws = socket.getAndSet(null);
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
