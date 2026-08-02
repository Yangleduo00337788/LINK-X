package com.linkx.server.controller;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FriendService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ChatController 聊天控制器集成测试")
class ChatControllerTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    private ConversationVO setupFriendConversation(TestUser a, TestUser b) {
        ensureFriends(a, b);
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }

    @Nested
    @DisplayName("GET /chat/sessions 获取会话列表测试")
    class ListSessionsTests {

        @Test
        @DisplayName("获取会话列表应成功")
        void listSessions_success() throws Exception {
            TestUser user = registerAndLogin("chatuser");

            mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录获取会话列表应返回401")
        void listSessions_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/sessions"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("GET /chat/search 搜索消息")
    class SearchTests {

        @Test
        @DisplayName("搜索应返回数组")
        void search_success() throws Exception {
            TestUser user = registerAndLogin("chatsearch");

            mockMvc.perform(get("/chat/search")
                            .param("q", "hello")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("未登录搜索应返回401")
        void search_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/search").param("q", "hello"))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST /chat/private/{friendId}")
    class OpenPrivateTests {

        @Test
        @DisplayName("非好友打开私聊应失败")
        void openPrivate_notFriend() throws Exception {
            TestUser a = registerAndLogin("chata");
            TestUser b = registerAndLogin("chatb");

            mockMvc.perform(post("/chat/private/{friendId}", b.userId)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(anyOf(is(400), is(403), is(404))));
        }
    }

    @Nested
    @DisplayName("POST /chat/sessions/{conversationId}/read 已读回执测试")
    class ReadReceiptTests {

        @Test
        @DisplayName("标记已读并返回未读数应成功")
        void markAsRead_success() throws Exception {
            TestUser a = registerAndLogin("reada");
            TestUser b = registerAndLogin("readb");

            SendFriendRequestDTO req = new SendFriendRequestDTO();
            req.setUsername(b.username);
            req.setMessage("hi");
            friendService.sendFriendRequest(a.userId, req);
            Long requestId = friendService.listIncomingRequests(b.userId).get(0).getId();
            friendService.acceptFriendRequest(b.userId, requestId);

            ConversationVO conv = chatService.getOrCreatePrivateConversation(a.userId, b.userId);

            // 先发送一条新消息给 a
            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("unread-1");
            MessageVO sent = chatService.sendMessage(b.userId, msg);

            // 标记已读，期望返回更新后的 unreadCount（此时 a 已读，对 a 为 0）
            mockMvc.perform(post("/chat/sessions/{conversationId}/read", conv.getId())
                            .param("lastMessageId", String.valueOf(sent.getId()))
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/chat/sessions/{conversationId}/unread", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(0));
        }
    }

    @Nested
    @DisplayName("GET /chat/sessions/{id}/messages 消息列表")
    class ListMessagesTests {

        @Test
        @DisplayName("分页拉取消息应成功")
        void listMessages_success() throws Exception {
            TestUser a = registerAndLogin("msglista");
            TestUser b = registerAndLogin("msglistb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("hello-list");
            chatService.sendMessage(a.userId, msg);

            mockMvc.perform(get("/chat/sessions/{conversationId}/messages", conv.getId())
                            .param("limit", "20")
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[?(@.content == 'hello-list')]").exists());
        }

        @Test
        @DisplayName("未登录拉取消息应返回401")
        void listMessages_unauthorized() throws Exception {
            mockMvc.perform(get("/chat/sessions/{conversationId}/messages", 1L))
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("POST pin/mute/important 会话开关")
    class ToggleSessionTests {

        @Test
        @DisplayName("置顶/重要/免打扰切换应成功")
        void togglePinMuteImportant_success() throws Exception {
            TestUser a = registerAndLogin("togglea");
            TestUser b = registerAndLogin("toggleb");
            ConversationVO conv = setupFriendConversation(a, b);

            mockMvc.perform(post("/chat/sessions/{conversationId}/pin", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(post("/chat/sessions/{conversationId}/important", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(post("/chat/sessions/{conversationId}/mute", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET unread / unread-total 未读汇总")
    class UnreadSummaryTests {

        @Test
        @DisplayName("单会话未读与总未读应一致")
        void unreadCounts_success() throws Exception {
            TestUser a = registerAndLogin("unreada");
            TestUser b = registerAndLogin("unreadb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("unread-ping");
            chatService.sendMessage(b.userId, msg);

            mockMvc.perform(get("/chat/sessions/{conversationId}/unread", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1));

            mockMvc.perform(get("/chat/unread-total")
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1));
        }
    }

    @Nested
    @DisplayName("会话草稿 save/get")
    class DraftTests {

        @Test
        @DisplayName("保存并读取草稿应成功")
        void draftSaveAndGet_success() throws Exception {
            TestUser a = registerAndLogin("drafta");
            TestUser b = registerAndLogin("draftb");
            ConversationVO conv = setupFriendConversation(a, b);

            String body = """
                    {"content":"draft text here"}
                    """;
            mockMvc.perform(post("/chat/sessions/{conversationId}/draft", conv.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/chat/sessions/{conversationId}/draft", conv.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("draft text here"));
        }
    }

    @Nested
    @DisplayName("消息撤回/编辑/转发/引用")
    class MessageActionTests {

        @Test
        @DisplayName("撤回消息应成功")
        void recallMessage_success() throws Exception {
            TestUser a = registerAndLogin("recalla");
            TestUser b = registerAndLogin("recallb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("to-recall");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(post("/chat/sessions/{cid}/messages/{mid}/recall", conv.getId(), sent.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.type").value("recall"));
        }

        @Test
        @DisplayName("编辑消息应成功")
        void editMessage_success() throws Exception {
            TestUser a = registerAndLogin("edita");
            TestUser b = registerAndLogin("editb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("before-edit");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(post("/chat/sessions/{cid}/messages/{mid}/edit", conv.getId(), sent.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"after-edit\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content").value("after-edit"));
        }

        @Test
        @DisplayName("转发消息到另一会话应成功")
        void forwardMessage_success() throws Exception {
            TestUser a = registerAndLogin("fwda");
            TestUser b = registerAndLogin("fwdb");
            TestUser c = registerAndLogin("fwdc");
            ensureFriends(a, b);
            ensureFriends(a, c);
            ConversationVO ab = chatService.getOrCreatePrivateConversation(a.userId, b.userId);
            ConversationVO ac = chatService.getOrCreatePrivateConversation(a.userId, c.userId);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(ab.getId());
            msg.setMsgType("text");
            msg.setContent("forward-me");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(post("/chat/sessions/{cid}/messages/{mid}/forward", ab.getId(), sent.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"targetConversationId":%d}
                                    """.formatted(ac.getId())))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.conversationId").value(ac.getId()))
                    .andExpect(jsonPath("$.data.content").value("forward-me"));
        }

        @Test
        @DisplayName("引用回复应成功")
        void quoteMessage_success() throws Exception {
            TestUser a = registerAndLogin("quotea");
            TestUser b = registerAndLogin("quoteb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO original = new SendMessageDTO();
            original.setConversationId(conv.getId());
            original.setMsgType("text");
            original.setContent("quote-src");
            MessageVO src = chatService.sendMessage(a.userId, original);

            String body = objectMapper.writeValueAsString(Map.of(
                    "conversationId", conv.getId(),
                    "msgType", "text",
                    "content", "quote-reply"
            ));
            mockMvc.perform(post("/chat/sessions/{cid}/messages/{mid}/quote", conv.getId(), src.getId())
                            .header("Authorization", b.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.quoteMessageId").value(src.getId()))
                    .andExpect(jsonPath("$.data.content").value("quote-reply"));
        }
    }

    @Nested
    @DisplayName("GET read-count 已读人数")
    class ReadCountTests {

        @Test
        @DisplayName("私聊消息已读人数应返回结构")
        void readCount_success() throws Exception {
            TestUser a = registerAndLogin("rca");
            TestUser b = registerAndLogin("rcb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("read-count-msg");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(get("/chat/sessions/{cid}/messages/{mid}/read-count", conv.getId(), sent.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.readCount").exists())
                    .andExpect(jsonPath("$.data.totalMembers").value(2));
        }
    }

    @Nested
    @DisplayName("分片上传 init/part/abort 与 check-hash")
    class UploadTests {

        @Test
        @DisplayName("init 与 abort 应成功")
        void initAndAbort_success() throws Exception {
            TestUser a = registerAndLogin("upinita");
            TestUser b = registerAndLogin("upinitb");
            ConversationVO conv = setupFriendConversation(a, b);

            MvcResult initResult = mockMvc.perform(post("/chat/sessions/{cid}/upload/init", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fileName":"test.zip","contentType":"application/zip","fileSize":1024}
                                    """))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
                    .andReturn();
            var data = objectMapper.readTree(initResult.getResponse().getContentAsString()).path("data");
            String uploadId = data.path("uploadId").asText();
            String objectName = data.path("objectName").asText();

            mockMvc.perform(post("/chat/sessions/{cid}/upload/abort", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"objectName":"%s","uploadId":"%s"}
                                    """.formatted(objectName, uploadId)))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("空分片上传应失败")
        void uploadPart_emptyRejected() throws Exception {
            TestUser a = registerAndLogin("upparta");
            TestUser b = registerAndLogin("uppartb");
            ConversationVO conv = setupFriendConversation(a, b);

            MvcResult initResult = mockMvc.perform(post("/chat/sessions/{cid}/upload/init", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fileName":"part.zip","contentType":"application/zip","fileSize":100}
                                    """))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            var data = objectMapper.readTree(initResult.getResponse().getContentAsString()).path("data");

            MockMultipartFile empty = new MockMultipartFile(
                    "file", "p.zip", "application/zip", new byte[0]);
            mockMvc.perform(multipart("/chat/sessions/{cid}/upload/part", conv.getId())
                            .file(empty)
                            .param("objectName", data.path("objectName").asText())
                            .param("uploadId", data.path("uploadId").asText())
                            .param("partNumber", "1")
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("未知哈希 check-hash 应返回 exists=false")
        void checkHash_notFound() throws Exception {
            TestUser user = registerAndLogin("hashchk");

            mockMvc.perform(post("/chat/upload/check-hash")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"hash":"%s","fileName":"a.zip","contentType":"application/zip","fileSize":100}
                                    """.formatted("a".repeat(64))))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.exists").value(false));
        }

        @Test
        @DisplayName("缺少 hash 应返回400")
        void checkHash_missingHash() throws Exception {
            TestUser user = registerAndLogin("hashbad");

            mockMvc.perform(post("/chat/upload/check-hash")
                            .header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("media-url 与 file 下载")
    class MediaDownloadTests {

        @Test
        @DisplayName("文本消息 refresh media-url 应返回预签名 URL")
        void mediaUrl_textMessage() throws Exception {
            TestUser a = registerAndLogin("murla");
            TestUser b = registerAndLogin("murlb");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("no-media");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(get("/chat/messages/{mid}/media-url", sent.getId())
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.url").exists());
        }

        @Test
        @DisplayName("非成员下载消息文件应失败")
        void downloadFile_notMember() throws Exception {
            TestUser a = registerAndLogin("dlfa");
            TestUser b = registerAndLogin("dlfb");
            TestUser outsider = registerAndLogin("dlfo");
            ConversationVO conv = setupFriendConversation(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("file-dl-guard");
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            mockMvc.perform(get("/chat/messages/{mid}/file", sent.getId())
                            .header("Authorization", outsider.bearer()))
                    .andExpect(jsonPath("$.code").value(anyOf(is(403), is(404))));
        }
    }
}
