package com.linkx.server.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
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

/**
 * 真实 Netty WebSocket 握手 / 心跳 / 发收推 / 离线 sync 集成测试。
 * <p>
 * 通过动态端口启动 IM WS，使用 JDK HttpClient WebSocket 客户端验证主链路。
 * </p>
 */
@DisplayName("IM WebSocket 真实链路 E2E")
class ImWebSocketLiveE2ETest extends BaseIntegrationTest {

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
    @DisplayName("合法 token 可握手，ping 应回 pong")
    void handshakeAndHeartbeat() throws Exception {
        TestUser user = registerAndLoginWithDevice("wsping", "ws-ping-" + UUID.randomUUID());
        try (WsSession session = connect(user.accessToken, "ws-ping-dev")) {
            assertTrue(session.awaitOpen(5, TimeUnit.SECONDS));
            session.send("""
                    {"action":"ping"}
                    """);
            JsonNode pong = session.awaitFrame(f -> "pong".equals(text(f, "action")), 5, TimeUnit.SECONDS);
            assertNotNull(pong);
            assertEquals("pong", pong.path("action").asText());
        }
    }

    @Test
    @DisplayName("双端在线：发送方收 ack，接收方收 message")
    void sendReceivePushChain() throws Exception {
        TestUser alice = registerAndLoginWithDevice("wsa", "ws-a-" + UUID.randomUUID());
        TestUser bob = registerAndLoginWithDevice("wsb", "ws-b-" + UUID.randomUUID());
        ConversationVO conv = becomeFriendsAndOpen(alice, bob);
        String clientMsgId = UUID.randomUUID().toString();
        String content = "hello-ws-" + System.nanoTime();

        try (WsSession aliceWs = connect(alice.accessToken, "ws-a-dev");
             WsSession bobWs = connect(bob.accessToken, "ws-b-dev")) {
            assertTrue(aliceWs.awaitOpen(5, TimeUnit.SECONDS));
            assertTrue(bobWs.awaitOpen(5, TimeUnit.SECONDS));

            aliceWs.send(String.format("""
                    {"action":"send","clientMsgId":"%s","conversationId":"%s","msgType":"text","content":"%s"}
                    """, clientMsgId, conv.getId(), content));

            JsonNode ack = aliceWs.awaitFrame(
                    f -> "ack".equals(text(f, "action")) && clientMsgId.equals(text(f, "clientMsgId")),
                    8, TimeUnit.SECONDS);
            assertNotNull(ack, "发送方应收到 ack");
            assertTrue(ack.path("serverMsgId").asLong() > 0);

            JsonNode pushed = bobWs.awaitFrame(f -> {
                if (!"message".equals(text(f, "action"))) return false;
                JsonNode data = f.path("data");
                return content.equals(data.path("content").asText());
            }, 8, TimeUnit.SECONDS);
            assertNotNull(pushed, "接收方应收到 message 推送");
            assertEquals(alice.userId, pushed.path("data").path("senderId").asLong());
        }
    }

    @Test
    @DisplayName("离线期间消息可经 sync 补齐（乱序/断线补偿）")
    void offlineThenSyncRecovers() throws Exception {
        TestUser alice = registerAndLoginWithDevice("wssynca", "ws-sa-" + UUID.randomUUID());
        TestUser bob = registerAndLoginWithDevice("wssyncb", "ws-sb-" + UUID.randomUUID());
        ConversationVO conv = becomeFriendsAndOpen(alice, bob);
        String content1 = "offline-1-" + System.nanoTime();
        String content2 = "offline-2-" + System.nanoTime();

        // Bob 先不上线；Alice 在线发送两条
        try (WsSession aliceWs = connect(alice.accessToken, "ws-sa-dev")) {
            assertTrue(aliceWs.awaitOpen(5, TimeUnit.SECONDS));
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            aliceWs.send(String.format("""
                    {"action":"send","clientMsgId":"%s","conversationId":"%s","msgType":"text","content":"%s"}
                    """, id1, conv.getId(), content1));
            aliceWs.awaitFrame(f -> "ack".equals(text(f, "action")) && id1.equals(text(f, "clientMsgId")),
                    8, TimeUnit.SECONDS);
            aliceWs.send(String.format("""
                    {"action":"send","clientMsgId":"%s","conversationId":"%s","msgType":"text","content":"%s"}
                    """, id2, conv.getId(), content2));
            aliceWs.awaitFrame(f -> "ack".equals(text(f, "action")) && id2.equals(text(f, "clientMsgId")),
                    8, TimeUnit.SECONDS);
        }

        // Bob 上线后 sync（lastServerMsgId=0 拉全量缺口）
        try (WsSession bobWs = connect(bob.accessToken, "ws-sb-dev")) {
            assertTrue(bobWs.awaitOpen(5, TimeUnit.SECONDS));
            bobWs.send("""
                    {"action":"sync","serverMsgId":0}
                    """);

            JsonNode m1 = bobWs.awaitFrame(f ->
                    "message".equals(text(f, "action"))
                            && content1.equals(f.path("data").path("content").asText()),
                    8, TimeUnit.SECONDS);
            JsonNode m2 = bobWs.awaitFrame(f ->
                    "message".equals(text(f, "action"))
                            && content2.equals(f.path("data").path("content").asText()),
                    8, TimeUnit.SECONDS);
            JsonNode done = bobWs.awaitFrame(f -> "syncDone".equals(text(f, "action")), 8, TimeUnit.SECONDS);

            assertNotNull(m1, "sync 应补齐第 1 条离线消息");
            assertNotNull(m2, "sync 应补齐第 2 条离线消息");
            assertNotNull(done, "应收到 syncDone");
            assertTrue(done.path("data").path("offlineCount").asInt() >= 2);
        }
    }

    @Test
    @DisplayName("无效 token 握手应失败")
    void invalidToken_rejected() {
        assertThrows(Exception.class, () -> {
            try (WsSession session = connect("not.a.valid.jwt", "bad-dev")) {
                session.awaitOpen(3, TimeUnit.SECONDS);
            }
        });
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("ws-e2e");
        friendService.sendFriendRequest(a.userId, req);
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        friendService.acceptFriendRequest(b.userId, incoming.get(0).getId());
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
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

    /** 收集入站 JSON 帧的简易 WS 会话。 */
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
                    // ignore malformed
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
                    // ignore
                }
            }
        }
    }
}
