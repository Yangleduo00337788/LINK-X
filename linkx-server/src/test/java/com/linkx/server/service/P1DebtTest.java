package com.linkx.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * P1 测试债：群权限越界、分片上传恢复、通知/未读一致性。
 */
@DisplayName("P1 测试债")
class P1DebtTest extends BaseIntegrationTest {

    private static final String MP_PARTS_PREFIX = "linkx:mp:parts:";

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageNotificationService notificationService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 25 群成员权限边界 ====================

    @Nested
    @DisplayName("群成员权限边界")
    class GroupPrivilegeBoundary {

        @Test
        @DisplayName("普通成员踢人/禁言/设管/解散/转让/改邀请策略应 403")
        void memberCannotEscalate() throws Exception {
            TestUser owner = registerAndLogin("p1own");
            TestUser member = registerAndLogin("p1mem");
            TestUser target = registerAndLogin("p1tgt");
            long cid = createGroup(owner, member, target);

            mockMvc.perform(delete("/group/{id}/members/{mid}", cid, target.userId)
                            .header("Authorization", member.bearer()))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(put("/group/{id}/members/{mid}/mute", cid, target.userId)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"muted\":true}"))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(put("/group/{id}/mute-all", cid)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"muted\":true}"))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(put("/group/{id}/members/{mid}/role", cid, target.userId)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"admin\"}"))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(delete("/group/{id}", cid)
                            .header("Authorization", member.bearer()))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(post("/group/{id}/transfer", cid)
                            .param("newOwnerId", String.valueOf(member.userId))
                            .header("Authorization", member.bearer()))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(post("/group/{id}/invite-policy", cid)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"policy\":\"ownerApprove\"}"))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(post("/group/{id}/members/batch-remove", cid)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"memberIds\":[%d]}", target.userId)))
                    .andExpect(jsonPath("$.code").value(403));

            mockMvc.perform(post("/group/{id}/members/batch-mute", cid)
                            .header("Authorization", member.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"memberIds\":[%d],\"muted\":true}", target.userId)))
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("群主可踢人；管理员可禁言普通成员")
        void ownerAndAdminAllowedOps() throws Exception {
            TestUser owner = registerAndLogin("p1owa");
            TestUser admin = registerAndLogin("p1ada");
            TestUser member = registerAndLogin("p1mma");
            TestUser kickee = registerAndLogin("p1kke");
            long cid = createGroup(owner, admin, member, kickee);

            mockMvc.perform(put("/group/{id}/members/{mid}/role", cid, admin.userId)
                            .header("Authorization", owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"admin\"}"))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(put("/group/{id}/members/{mid}/mute", cid, member.userId)
                            .header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"muted\":true}"))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(delete("/group/{id}/members/{mid}", cid, kickee.userId)
                            .header("Authorization", owner.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 26 分片上传失败与恢复 ====================

    @Nested
    @DisplayName("分片上传失败与恢复")
    class MultipartUploadResume {

        @Test
        @DisplayName("中断后 list parts 可恢复已上传分片，重传同 part 幂等")
        void resumeAfterPartialUpload() throws Exception {
            TestUser a = registerAndLogin("p1upa");
            TestUser b = registerAndLogin("p1upb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            String initBody = """
                    {"fileName":"resume.zip","contentType":"application/zip","fileSize":10485760}
                    """;
            String initResp = mockMvc.perform(post("/chat/sessions/{cid}/upload/init", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(initBody))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
                    .andExpect(jsonPath("$.data.objectName").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            JsonNode data = objectMapper.readTree(initResp).path("data");
            String uploadId = data.path("uploadId").asText();
            String objectName = data.path("objectName").asText();

            // 模拟 part1 已成功写入后客户端崩溃（跳过 MinIO，直接落 Redis）
            String etag1 = "etag-part-1-resume";
            stringRedisTemplate.opsForHash().put(MP_PARTS_PREFIX + uploadId, "1", etag1);

            mockMvc.perform(get("/chat/sessions/{cid}/upload/{uploadId}/parts", conv.getId(), uploadId)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].partNumber").value(1))
                    .andExpect(jsonPath("$.data[0].etag").value(etag1));

            // 恢复后续传同 part：应幂等返回原 etag，不依赖 MinIO
            MockMultipartFile part = new MockMultipartFile(
                    "file", "part1.zip", "application/zip", new byte[]{1, 2, 3});
            mockMvc.perform(multipart("/chat/sessions/{cid}/upload/part", conv.getId())
                            .file(part)
                            .param("objectName", objectName)
                            .param("uploadId", uploadId)
                            .param("partNumber", "1")
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.etag").value(etag1));

            mockMvc.perform(post("/chat/sessions/{cid}/upload/abort", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {"objectName":"%s","uploadId":"%s"}
                                    """, objectName, uploadId)))
                    .andExpect(jsonPath("$.code").value(200));

            // abort 后会话应不可用
            mockMvc.perform(get("/chat/sessions/{cid}/upload/{uploadId}/parts", conv.getId(), uploadId)
                            .header("Authorization", a.bearer()))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("空分片上传应失败")
        void emptyPartRejected() throws Exception {
            TestUser a = registerAndLogin("p1upe");
            TestUser b = registerAndLogin("p1upf");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            String initResp = mockMvc.perform(post("/chat/sessions/{cid}/upload/init", conv.getId())
                            .header("Authorization", a.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fileName":"empty.zip","contentType":"application/zip","fileSize":100}
                                    """))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = objectMapper.readTree(initResp).path("data");

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
    }

    // ==================== 27 通知与未读同步 ====================

    @Nested
    @DisplayName("通知到达与未读角标一致性")
    class NotificationUnreadSync {

        @Test
        @DisplayName("私聊消息到达后会话未读/总未读一致，已读后归零")
        void chatUnreadBadgeConsistent() throws Exception {
            TestUser a = registerAndLogin("p1ura");
            TestUser b = registerAndLogin("p1urb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("unread-sync-" + UUID.randomUUID());
            msg.setClientMsgId(UUID.randomUUID().toString());
            MessageVO sent = chatService.sendMessage(a.userId, msg);

            String unreadResp = mockMvc.perform(get("/chat/sessions/{cid}/unread", conv.getId())
                            .header("Authorization", b.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            long sessionUnread = objectMapper.readTree(unreadResp).path("data").asLong();
            assertTrue(sessionUnread >= 1);

            String totalResp = mockMvc.perform(get("/chat/unread-total")
                            .header("Authorization", b.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            long totalUnread = objectMapper.readTree(totalResp).path("data").asLong();
            assertTrue(totalUnread >= sessionUnread);

            String sessionsResp = mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", b.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            long listedUnread = 0;
            String convIdStr = String.valueOf(conv.getId());
            for (JsonNode node : objectMapper.readTree(sessionsResp).path("data")) {
                if (convIdStr.equals(node.path("id").asText())) {
                    listedUnread = node.path("unreadCount").asLong();
                    break;
                }
            }
            assertEquals(sessionUnread, listedUnread, "会话列表 unreadCount 应与 unread 接口一致");

            mockMvc.perform(post("/chat/sessions/{cid}/read", conv.getId())
                            .param("lastMessageId", String.valueOf(sent.getId()))
                            .header("Authorization", b.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            String afterRead = mockMvc.perform(get("/chat/sessions/{cid}/unread", conv.getId())
                            .header("Authorization", b.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(0, objectMapper.readTree(afterRead).path("data").asLong());
        }

        @Test
        @DisplayName("业务通知创建后 unread-count 与 unread 列表一致，标已读后归零")
        void notificationUnreadConsistent() throws Exception {
            TestUser receiver = registerAndLogin("p1nra");
            TestUser sender = registerAndLogin("p1nrb");

            notificationService.create(
                    receiver.userId, sender.userId, null, null,
                    "moments_like", 1001L, "点赞了你的动态");

            String countResp = mockMvc.perform(get("/notifications/unread-count")
                            .header("Authorization", receiver.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn().getResponse().getContentAsString();
            int count = objectMapper.readTree(countResp).path("data").path("count").asInt();
            assertTrue(count >= 1);

            String listResp = mockMvc.perform(get("/notifications/unread")
                            .header("Authorization", receiver.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andReturn().getResponse().getContentAsString();
            int listSize = objectMapper.readTree(listResp).path("data").size();
            assertEquals(count, listSize, "未读通知数应与未读列表长度一致");

            long notificationId = objectMapper.readTree(listResp).path("data").get(0).path("id").asLong();
            mockMvc.perform(post("/notifications/{id}/read", notificationId)
                            .header("Authorization", receiver.bearer()))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/notifications/unread-count")
                            .header("Authorization", receiver.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.count").value(0));
        }
    }

    // ---- helpers ----

    private long createGroup(TestUser owner, TestUser... members) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < members.length; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append(members[i].userId);
        }
        String body = String.format("""
                {"name":"P1权限群","memberIds":[%s]}
                """, ids);
        String resp = mockMvc.perform(post("/group")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private ConversationVO becomeFriendsAndOpen(TestUser a, TestUser b) {
        SendFriendRequestDTO req = new SendFriendRequestDTO();
        req.setUsername(b.username);
        req.setMessage("hi");
        friendService.sendFriendRequest(a.userId, req);
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(b.userId);
        friendService.acceptFriendRequest(b.userId, incoming.get(0).getId());
        return chatService.getOrCreatePrivateConversation(a.userId, b.userId);
    }
}
