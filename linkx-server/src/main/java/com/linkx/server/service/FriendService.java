package com.linkx.server.service;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.controller.vo.UserSearchVO;

import java.util.List;

public interface FriendService {

    List<UserSearchVO> searchUsers(String keyword, Long currentUserId);

    void sendFriendRequest(Long fromUserId, SendFriendRequestDTO dto);

    List<FriendRequestVO> listIncomingRequests(Long userId);

    List<FriendRequestVO> listOutgoingRequests(Long userId);

    void acceptFriendRequest(Long userId, Long requestId);

    void rejectFriendRequest(Long userId, Long requestId);

    /**
     * 清空当前用户已处理的好友申请记录（已同意/已拒绝），保留待处理申请。
     *
     * @return 清空的记录数
     */
    int clearProcessedFriendRequests(Long userId);

    List<FriendItemVO> listFriends(Long userId);

    void deleteFriend(Long userId, Long friendId);

    /** 更新好友备注（须为好友），返回规范化后的备注 */
    String updateFriendRemark(Long userId, Long friendId, String remark);

    /** 更新好友分组名（须为好友），返回规范化后的分组名 */
    String updateFriendGroup(Long userId, Long friendId, String groupName);

    /** 屏蔽好友（status=已拉黑，会话仍可见但不可发消息） */
    void blockFriend(Long userId, Long friendId);

    /** 取消屏蔽 */
    void unblockFriend(Long userId, Long friendId);

    /** 当前用户是否已屏蔽对方 */
    boolean isBlocked(Long userId, Long friendId);

    /**
     * 谁把 {@code userId} 当好友（反向关系，status=正常），用于 presence 扇出。
     */
    List<Long> listWatcherIds(Long userId);
}
