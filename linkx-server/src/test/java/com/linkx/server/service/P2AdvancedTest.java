package com.linkx.server.service;

import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.DeviceVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserDataExportVO;
import com.linkx.server.entity.DeviceSession;
import com.linkx.server.exception.CustomException;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * P2 进阶能力：合规导出、会议创建、设备列表。
 */
@DisplayName("P2 进阶功能测试")
class P2AdvancedTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private ComplianceService complianceService;
    @Autowired
    private ConferenceService conferenceService;
    @Autowired
    private DeviceSessionService deviceSessionService;
    @Autowired
    private TokenService tokenService;

    @Nested
    @DisplayName("合规导出")
    class Compliance {

        @Test
        @DisplayName("exportUserData 应返回用户快照")
        void exportUserData_ok() {
            TestUser user = registerAndLogin("p2exp");
            UserDataExportVO vo = complianceService.exportUserData(user.userId);

            assertEquals(user.userId, vo.getUserId());
            assertEquals(user.username, vo.getUsername());
            assertNotNull(vo.getExportTime());
            assertNotNull(vo.getFriends());
            assertNotNull(vo.getConversations());
            assertNotNull(vo.getDevices());
        }

        @Test
        @DisplayName("HTTP GET /compliance/export 应成功")
        void exportViaHttp() throws Exception {
            TestUser user = registerAndLogin("p2exph");
            mockMvc.perform(get("/compliance/export")
                            .header("Authorization", user.bearer()))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.userId").value(user.userId))
                    .andExpect(jsonPath("$.data.username").value(user.username));
        }

        @Test
        @DisplayName("audit 记录不应抛异常")
        void audit_ok() {
            TestUser user = registerAndLogin("p2aud");
            assertDoesNotThrow(() ->
                    complianceService.audit(user.userId, "TEST_EXPORT", "unit-test", true));
        }
    }

    @Nested
    @DisplayName("多人会议")
    class Conference {

        @Test
        @DisplayName("好友会话上创建会议应成功")
        void createConference_ok() {
            TestUser a = registerAndLogin("p2cfa");
            TestUser b = registerAndLogin("p2cfb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            ConferenceCreateDTO dto = new ConferenceCreateDTO();
            dto.setConversationId(conv.getId());
            dto.setType("video");
            dto.setTitle("测试会议");
            dto.setMaxParticipants(6);

            ConferenceInfoVO info = conferenceService.create(a.userId, dto);
            assertNotNull(info.getId());
            assertEquals("测试会议", info.getTitle());
            assertEquals("video", info.getType());
            assertEquals(a.userId, info.getCreatorId());
            assertEquals(conv.getId(), info.getConversationId());
            assertNotNull(info.getCallId());

            ConferenceInfoVO loaded = conferenceService.info(info.getId());
            assertEquals(info.getId(), loaded.getId());
        }

        @Test
        @DisplayName("非会话成员创建会议应失败")
        void createConference_nonMember_fails() {
            TestUser a = registerAndLogin("p2cna");
            TestUser b = registerAndLogin("p2cnb");
            TestUser stranger = registerAndLogin("p2cns");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            ConferenceCreateDTO dto = new ConferenceCreateDTO();
            dto.setConversationId(conv.getId());
            dto.setType("voice");
            assertThrows(CustomException.class,
                    () -> conferenceService.create(stranger.userId, dto));
        }

        @Test
        @DisplayName("创建后 listActive 应包含该会议")
        void listActive_containsCreated() {
            TestUser a = registerAndLogin("p2cla");
            TestUser b = registerAndLogin("p2clb");
            ConversationVO conv = becomeFriendsAndOpen(a, b);

            ConferenceCreateDTO dto = new ConferenceCreateDTO();
            dto.setConversationId(conv.getId());
            dto.setType("video");
            ConferenceInfoVO created = conferenceService.create(a.userId, dto);

            List<ConferenceInfoVO> active = conferenceService.listActive(a.userId);
            assertTrue(active.stream().anyMatch(c -> created.getId().equals(c.getId())));
        }

        @Test
        @DisplayName("双方均在会议中时可交换信令（非 mesh，走房间信令转发）")
        void signal_betweenHostAndJoiner() {
            TestUser host = registerAndLogin("p2csa");
            TestUser peer = registerAndLogin("p2csb");
            ConversationVO conv = becomeFriendsAndOpen(host, peer);

            ConferenceCreateDTO dto = new ConferenceCreateDTO();
            dto.setConversationId(conv.getId());
            dto.setType("video");
            dto.setTitle("信令冒烟");
            ConferenceInfoVO created = conferenceService.create(host.userId, dto);

            ConferenceInfoVO joined = conferenceService.join(peer.userId, created.getId(), null);
            assertEquals(created.getId(), joined.getId());

            ConferenceSignalDTO offer = new ConferenceSignalDTO();
            offer.setConferenceId(created.getId());
            offer.setSignalType("offer");
            offer.setSdp("v=0-offer-smoke");
            offer.setTargetUserId(peer.userId);
            assertDoesNotThrow(() -> conferenceService.signal(host.userId, offer));

            ConferenceSignalDTO answer = new ConferenceSignalDTO();
            answer.setConferenceId(created.getId());
            answer.setSignalType("answer");
            answer.setSdp("v=0-answer-smoke");
            answer.setTargetUserId(host.userId);
            assertDoesNotThrow(() -> conferenceService.signal(peer.userId, answer));
        }
    }

    @Nested
    @DisplayName("设备列表")
    class Devices {

        @Test
        @DisplayName("注册设备后 listByUser 应包含该设备")
        void registerAndList() {
            TestUser user = registerAndLogin("p2dev");
            String deviceId = "dev-" + UUID.randomUUID();
            DeviceSession session = deviceSessionService.registerDevice(
                    user.userId, deviceId, "JUnit Phone", "Android", "127.0.0.1", "JUnitAgent");
            assertNotNull(session);
            assertEquals(deviceId, session.getDeviceId());

            List<DeviceVO> devices = deviceSessionService.listByUser(user.userId, deviceId);
            assertTrue(devices.stream().anyMatch(d -> deviceId.equals(d.getId())));
            assertTrue(devices.stream()
                    .filter(d -> deviceId.equals(d.getId()))
                    .anyMatch(DeviceVO::isCurrent));
        }

        @Test
        @DisplayName("HTTP GET /user/devices 应返回列表")
        void listDevicesViaHttp() throws Exception {
            TestUser user = registerAndLogin("p2devh");
            String deviceId = "http-dev-" + UUID.randomUUID();
            deviceSessionService.createOrUpdate(
                    user.userId, deviceId, "Web", "Web", "127.0.0.1", "Test");

            mockMvc.perform(get("/user/devices")
                            .header("Authorization", user.bearer())
                            .header("X-Device-Id", deviceId))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("删除设备后列表中应消失")
        void deleteDevice_removes() {
            TestUser user = registerAndLogin("p2del");
            String deviceId = "del-" + UUID.randomUUID();
            deviceSessionService.createOrUpdate(
                    user.userId, deviceId, "Temp", "Web", "127.0.0.1", null);
            deviceSessionService.deleteDevice(user.userId, deviceId);

            List<DeviceVO> devices = deviceSessionService.listByUser(user.userId, null);
            assertTrue(devices.stream().noneMatch(d -> deviceId.equals(d.getId())));
        }

        @Test
        @DisplayName("kickDevice 应吊销设备 token 并拒绝同设备访问")
        void kickDevice_revokesToken() throws Exception {
            String deviceId = "kick-" + UUID.randomUUID();
            TestUser user = registerAndLoginWithDevice("p2kick", deviceId);

            deviceSessionService.kickDevice(user.userId, deviceId, user.username, "127.0.0.1", "JUnit");

            assertTrue(tokenService.isDeviceKicked(user.userId, deviceId));
            List<DeviceVO> devices = deviceSessionService.listByUser(user.userId, deviceId);
            assertTrue(devices.stream().noneMatch(d -> deviceId.equals(d.getId())));

            mockMvc.perform(get("/user/devices")
                            .header("Authorization", user.bearer())
                            .header("X-Device-Id", deviceId))
                    .andExpect(jsonPath("$.code").value(401));
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
}
