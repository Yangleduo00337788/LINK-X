package com.linkx.server.service;

import com.linkx.server.controller.vo.FriendItemVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FriendService 好友服务测试
 */
@DisplayName("FriendService 好友服务测试")
class FriendServiceTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;

    @Nested
    @DisplayName("searchUsers 搜索用户测试")
    class SearchUsersTests {

        @Test
        @DisplayName("搜索应返回结果列表")
        void search_returnsResults() {
            List<?> results = friendService.searchUsers("test", 1L);
            assertNotNull(results);
        }
    }

    @Nested
    @DisplayName("listFriends 获取好友列表测试")
    class ListFriendsTests {

        @Test
        @DisplayName("获取好友列表应成功")
        void listFriends_success() {
            List<FriendItemVO> friends = friendService.listFriends(1L);
            assertNotNull(friends);
        }
    }

    @Nested
    @DisplayName("listIncomingRequests 获取收到的好友申请测试")
    class IncomingRequestsTests {

        @Test
        @DisplayName("获取收到的好友申请应成功")
        void listIncomingRequests_success() {
            List<FriendRequestVO> requests = friendService.listIncomingRequests(1L);
            assertNotNull(requests);
        }
    }

    @Nested
    @DisplayName("listOutgoingRequests 获取发出的好友申请测试")
    class OutgoingRequestsTests {

        @Test
        @DisplayName("获取发出的好友申请应成功")
        void listOutgoingRequests_success() {
            List<FriendRequestVO> requests = friendService.listOutgoingRequests(1L);
            assertNotNull(requests);
        }
    }
}
