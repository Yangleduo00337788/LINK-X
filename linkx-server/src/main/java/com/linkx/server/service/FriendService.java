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
}
