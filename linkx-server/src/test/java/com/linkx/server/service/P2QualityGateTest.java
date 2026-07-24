package com.linkx.server.service;

import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.support.BaseIntegrationTest;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * P2 质量门禁：多端同步、审计落库、大群扇出冒烟。
 */
@DisplayName("P2 质量门禁测试")
class P2QualityGateTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Test1234abcd";

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private DeviceSessionService deviceSessionService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private ComplianceService complianceService;
    @Autowired
    private SysAuditLogMapper auditLogMapper;

    // ==================== 42 多端登录与状态同步 ====================

    @Nested
    @DisplayName("多端登录与状态同步")
    class MultiDeviceSync {

        @Test
        @DisplayName("双端登录均出现在设备列表，且互不踢下线")
        void dualLogin_bothDevicesListed() throws Exception {
            String phoneId = "phone-" + UUID.randomUUID();
            String webId = "web-" + UUID.randomUUID();
            TestUser phone = registerAndLoginWithDevice("p2mda", phoneId);
            TestUser web = login(phone.username, PASSWORD, webId);

            List<DeviceVO> fromPhone = deviceSessionService.listByUser(phone.userId, phoneId);
            List<DeviceVO> fromWeb = deviceSessionService.listByUser(web.userId, webId);

            assertTrue(fromPhone.stream().anyMatch(d -> phoneId.equals(d.getId())));
            assertTrue(fromPhone.stream().anyMatch(d -> webId.equals(d.getId())));
            assertTrue(fromWeb.stream().anyMatch(d -> phoneId.equals(d.getId())));
            assertTrue(fromWeb.stream().anyMatch(d -> webId.equals(d.getId())));

            mockMvc.perform(get("/user/devices")
                            .header("Authorization", phone.bearer())
                            .header("X-Device-Id", phoneId))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(get("/user/devices")
                            .header("Authorization", web.bearer())
                            .header("X-Device-Id", webId))
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("一端标记已读后，另一端未读归零（用户级同步）")
        void readOnOneDevice_syncsUnread() {
            String d1 = "rd1-" + UUID.randomUUID();
            String d2 = "rd2-" + UUID.randomUUID();
            TestUser bobPhone = registerAndLoginWithDevice("p2rdb", d1);
            TestUser bobWeb = login(bobPhone.username, PASSWORD, d2);
            TestUser alice = registerAndLogin("p2rda");

            ConversationVO conv = becomeFriendsAndOpen(alice, bobPhone);

            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(conv.getId());
            msg.setMsgType("text");
            msg.setContent("multi-device-unread");
            msg.setClientMsgId(UUID.randomUUID().toString());
            MessageVO sent = chatService.sendMessage(alice.userId, msg);

            assertTrue(chatService.getUnreadCount(bobPhone.userId, conv.getId()) >= 1);
            assertTrue(chatService.getUnreadCount(bobWeb.userId, conv.getId()) >= 1);

            // 手机端已读
            chatService.markAsRead(bobPhone.userId, conv.getId(), sent.getId());

            assertEquals(0, chatService.getUnreadCount(bobPhone.userId, conv.getId()));
            assertEquals(0, chatService.getUnreadCount(bobWeb.userId, conv.getId()));
            assertEquals(0, chatService.getTotalUnreadCount(bobWeb.userId));
        }
    }

    // ==================== 44 审计日志完整性 ====================

    @Nested
    @DisplayName("审计日志完整性")
    class AuditIntegrity {

        @Test
        @DisplayName("登录 / 踢设备 / 数据导出应落库审计")
        void criticalOps_persistAuditRows() throws Exception {
            String deviceId = "aud-" + UUID.randomUUID();
            TestUser user = registerAndLoginWithDevice("p2aud", deviceId);

            assertTrue(awaitAuditExists(user.userId, "LOGIN"), "登录应写入 LOGIN 审计");

            deviceSessionService.kickDevice(user.userId, deviceId, user.username, "127.0.0.1", "JUnit");
            assertTrue(awaitAuditExists(user.userId, "DEVICE_KICK"), "踢下线应写入 DEVICE_KICK 审计");

            complianceService.exportUserData(user.userId);
            assertTrue(awaitAuditExists(user.userId, "DATA_EXPORT"), "数据导出应写入 DATA_EXPORT 审计");

            TestUser other = registerAndLogin("p2audh");
            mockMvc.perform(get("/compliance/export")
                            .header("Authorization", other.bearer()))
                    .andExpect(jsonPath("$.code").value(200));
            assertTrue(awaitAuditExists(other.userId, "DATA_EXPORT"));
        }

        @Test
        @DisplayName("注册应写入 REGISTER 审计")
        void register_writesAudit() throws Exception {
            String username = "itu" + System.nanoTime();
            if (username.length() > 32) {
                username = username.substring(0, 32);
            }
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {"username":"%s","password":"%s","nickname":"audreg","email":"%s@linkx.test"}
                                    """, username, PASSWORD, username)))
                    .andExpect(jsonPath("$.code").value(200));

            assertTrue(awaitAuditByUsername(username, "REGISTER"), "注册应写入 REGISTER 审计");

            TestUser logged = login(username, PASSWORD);
            assertTrue(awaitAuditExists(logged.userId, "LOGIN"));
        }
    }

    // ==================== 46 大群消息扇出冒烟 ====================

    @Nested
    @DisplayName("大群消息扇出冒烟")
    class GroupFanoutSmoke {

        @Test
        @DisplayName("群主发消息后多名成员均可拉取到")
        void groupMessage_visibleToAllMembers() {
            TestUser owner = registerAndLogin("p2gfo");
            List<TestUser> members = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                members.add(registerAndLogin("p2gfm" + i));
            }

            CreateGroupDTO dto = new CreateGroupDTO();
            dto.setName("扇出压测群");
            dto.setMemberIds(members.stream().map(m -> m.userId).collect(Collectors.toList()));
            GroupConversationVO group = groupService.createGroup(owner.userId, dto);
            assertNotNull(group.getId());

            long t0 = System.nanoTime();
            SendMessageDTO msg = new SendMessageDTO();
            msg.setConversationId(group.getId());
            msg.setMsgType("text");
            msg.setContent("fanout-" + UUID.randomUUID());
            msg.setClientMsgId(UUID.randomUUID().toString());
            MessageVO sent = chatService.sendMessage(owner.userId, msg);
            long sendMs = (System.nanoTime() - t0) / 1_000_000L;
            assertTrue(sendMs < 15_000, "40 人扇出发送应在 15s 内完成，实际=" + sendMs + "ms");

            for (TestUser m : members) {
                List<MessageVO> hist = chatService.listMessages(m.userId, group.getId(), null, 20);
                assertTrue(hist.stream().anyMatch(x -> sent.getId().equals(x.getId())),
                        "成员 " + m.username + " 应能看到群消息");
                assertTrue(chatService.getUnreadCount(m.userId, group.getId()) >= 1);
            }
        }
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

    /** 审计写入为 @Async，短轮询等待落库。 */
    private boolean awaitAuditExists(Long userId, String operationType) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            List<SysAuditLog> logs = auditLogMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq("user_id", userId)
                            .eq("operation_type", operationType));
            if (!logs.isEmpty()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private boolean awaitAuditByUsername(String username, String operationType) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            List<SysAuditLog> logs = auditLogMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq("username", username)
                            .eq("operation_type", operationType));
            if (!logs.isEmpty()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
