package com.linkx.server.it;

import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 好友核心链路集成测试：搜索 → 发申请 → 通过 → 好友列表 → 备注 → 屏蔽 → 删除。
 */
class FriendFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REGISTER_CODE = "886688";

    private Long registerAndLogin(String username) {
        String email = username + "@linkx-it.local";
        stringRedisTemplate.opsForValue().set(
                "linkx:register-email:" + email, REGISTER_CODE, Duration.ofMinutes(10));
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(TEST_PASSWORD);
        dto.setNickname("测试-" + username);
        dto.setEmail(email);
        dto.setEmailCode(REGISTER_CODE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        sysUserService.register(dto, request);
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(TEST_PASSWORD);
        TokenVO tokenVO = sysUserService.login(loginDTO, "127.0.0.1", "LinkX-IT/1.0", request);
        return tokenVO.getUser().getId();
    }

    @Test
    void friendRequest_accept_list_fullChain() {
        String a = uniqueName("fda");
        String b = uniqueName("fdb");
        Long userA = registerAndLogin(a);
        Long userB = registerAndLogin(b);

        // 1. 搜索能找到对方
        List<?> hits = friendService.searchUsers(b, userA);
        assertThat(hits).extracting("id").contains(userB);

        // 2. A 向 B 发好友申请
        SendFriendRequestDTO sendDTO = new SendFriendRequestDTO();
        sendDTO.setUsername(b);
        sendDTO.setMessage("交个朋友");
        friendService.sendFriendRequest(userA, sendDTO);

        // 3. B 的 incoming 列表可见，A 的 outgoing 可见
        List<FriendRequestVO> incoming = friendService.listIncomingRequests(userB);
        assertThat(incoming).extracting("fromUserId").contains(userA);
        Long requestId = incoming.stream()
                .filter(r -> r.getFromUserId().equals(userA))
                .findFirst().orElseThrow()
                .getId();
        assertThat(friendService.listOutgoingRequests(userA)).isNotEmpty();

        // 4. B 通过申请 → 双方好友列表互见
        friendService.acceptFriendRequest(userB, requestId);
        assertThat(friendService.listFriends(userA)).extracting("userId").contains(userB);
        assertThat(friendService.listFriends(userB)).extracting("userId").contains(userA);

        // 5. 备注更新需好友关系；对非好友会失败
        assertThat(friendService.updateFriendRemark(userA, userB, "老朋友")).isEqualTo("老朋友");
    }

    @Test
    void nonFriend_cannotOperate() {
        String a = uniqueName("npc");
        String b = uniqueName("npd");
        Long userA = registerAndLogin(a);
        Long userB = registerAndLogin(b);

        // 未建立好友关系时更新备注应被拒绝
        assertThatThrownBy(() -> friendService.updateFriendRemark(userA, userB, "x"))
                .isInstanceOf(CustomException.class);
        // 屏蔽非好友也应被拒绝（或依赖关系校验）
        assertThatThrownBy(() -> friendService.blockFriend(userA, userB))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void blockFriend_blocksAndUnblocks() {
        String a = uniqueName("bla");
        String b = uniqueName("blb");
        Long userA = registerAndLogin(a);
        Long userB = registerAndLogin(b);

        SendFriendRequestDTO sendDTO = new SendFriendRequestDTO();
        sendDTO.setUsername(b);
        friendService.sendFriendRequest(userA, sendDTO);
        FriendRequestVO req = friendService.listIncomingRequests(userB).stream()
                .filter(r -> r.getFromUserId().equals(userA)).findFirst().orElseThrow();
        friendService.acceptFriendRequest(userB, req.getId());

        friendService.blockFriend(userA, userB);
        assertThat(friendService.isBlocked(userA, userB)).isTrue();

        friendService.unblockFriend(userA, userB);
        assertThat(friendService.isBlocked(userA, userB)).isFalse();

        // 删除好友
        friendService.deleteFriend(userA, userB);
        assertThat(friendService.listFriends(userA)).extracting("userId").doesNotContain(userB);
    }
}
