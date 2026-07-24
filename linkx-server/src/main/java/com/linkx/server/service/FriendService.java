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

    List<FriendItemVO> listFriends(Long userId);

    void deleteFriend(Long userId, Long friendId);

    /** 屏蔽好友（status=已拉黑，会话仍可见但不可发消息） */
    void blockFriend(Long userId, Long friendId);

    /** 取消屏蔽 */
    void unblockFriend(Long userId, Long friendId);

    /** 当前用户是否已屏蔽对方 */
    boolean isBlocked(Long userId, Long friendId);
}
