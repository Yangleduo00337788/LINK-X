package com.linkx.server.it;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupMemberVO;
import com.linkx.server.controller.vo.UserSearchVO;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.FriendService;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 群聊权限核心链路集成测试：建群 → 权限门禁 → 提管理员 → 加人 → 转让 → 解散。
 * 注：群内邀请要求双方为好友，故先建立好友关系。
 */
class GroupPermissionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GroupService groupService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 注册用户并返回 [userId, username] */
    private String[] registerNamed(String prefix) {
        String username = uniqueName(prefix);
        Long id = registerUser(username, sysUserService, stringRedisTemplate);
        return new String[]{String.valueOf(id), username};
    }

    /** A 向 B 发申请并由 B 通过，建立双向好友关系 */
    private void becomeFriends(String usernameA, Long userA, String usernameB, Long userB) {
        SendFriendRequestDTO dto = new SendFriendRequestDTO();
        dto.setUsername(usernameB);
        friendService.sendFriendRequest(userA, dto);
        Long requestId = friendService.listIncomingRequests(userB).stream()
                .filter(r -> r.getFromUserId().equals(userA)).findFirst().orElseThrow().getId();
        friendService.acceptFriendRequest(userB, requestId);
    }

    @Test
    void groupLifecycle_withPermissionChecks() {
        String[] ownerArr = registerNamed("gowner");
        Long owner = Long.valueOf(ownerArr[0]);
        String ownerName = ownerArr[1];

        String[] memberArr = registerNamed("gmember");
        Long member = Long.valueOf(memberArr[0]);
        String memberName = memberArr[1];

        String[] outsiderArr = registerNamed("gout");
        Long outsider = Long.valueOf(outsiderArr[0]);

        String[] newcomerArr = registerNamed("gnew");
        Long newcomer = Long.valueOf(newcomerArr[0]);
        String newcomerName = newcomerArr[1];

        // 群主分别与成员/新成员结为好友（邀请要求好友关系）
        becomeFriends(ownerName, owner, memberName, member);
        becomeFriends(ownerName, owner, newcomerName, newcomer);

        // 1. 建群
        CreateGroupDTO createDTO = new CreateGroupDTO();
        createDTO.setName("集成测试群");
        createDTO.setMemberIds(List.of(member));
        GroupConversationVO group = groupService.createGroup(owner, createDTO);
        Long groupId = group.getId();
        assertThat(group.getOwnerId()).isEqualTo(owner);

        // 2. 成员列表：群主 + member
        List<GroupMemberVO> members = groupService.listMembers(owner, groupId);
        assertThat(members).extracting(GroupMemberVO::getUserId)
                .containsExactlyInAnyOrder(owner, member);

        // 3. 普通成员不能踢人、不能解散
        assertThatThrownBy(() -> groupService.removeMember(member, groupId, owner))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> groupService.dissolveGroup(member, groupId))
                .isInstanceOf(CustomException.class);

        // 4. 群外用户读不到群成员
        assertThatThrownBy(() -> groupService.listMembers(outsider, groupId))
                .isInstanceOf(CustomException.class);

        // 5. 群主提升 member 为管理员，管理员可加人
        groupService.updateMemberRole(owner, groupId, member, "admin");
        assertThat(groupService.listMembers(owner, groupId))
                .filteredOn(m -> m.getUserId().equals(member))
                .allSatisfy(m -> assertThat(m.getRole()).isEqualTo("admin"));

        AddGroupMembersDTO addDTO = new AddGroupMembersDTO();
        addDTO.setMemberIds(List.of(newcomer));
        groupService.addMembers(member, groupId, addDTO);
        assertThat(groupService.listMembers(owner, groupId))
                .extracting(GroupMemberVO::getUserId).contains(newcomer);

        // 6. 管理员不能转让群主
        assertThatThrownBy(() -> groupService.transferOwner(member, groupId, newcomer))
                .isInstanceOf(CustomException.class);

        // 7. 群主转让给 newcomer，原群主变为非 owner 角色
        groupService.transferOwner(owner, groupId, newcomer);
        assertThat(groupService.listMembers(newcomer, groupId))
                .filteredOn(m -> m.getUserId().equals(owner))
                .allSatisfy(m -> assertThat(m.getRole()).isNotEqualTo("owner"));

        // 8. 新群主解散群
        groupService.dissolveGroup(newcomer, groupId);
        assertThatThrownBy(() -> groupService.listMembers(newcomer, groupId))
                .isInstanceOf(CustomException.class);
    }
}
