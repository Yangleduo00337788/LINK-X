package com.linkx.server.service;

import com.linkx.server.controller.dto.SendFriendRequestDTO;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.ChatSearchHitVO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.FriendRequestVO;
import com.linkx.server.service.impl.ChatServiceImpl;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("聊天搜索：时间范围与高亮")
class ChatSearchEnhanceTest extends BaseIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("buildSearchHighlight 应转义并包裹关键词")
    void highlight_escapesAndMarks() {
        String html = ChatServiceImpl.buildSearchHighlight("你好 <b>world</b> 测试", "world");
        assertTrue(html.contains("<mark>world</mark>"));
        assertTrue(html.contains("&lt;b&gt;"));
        assertFalse(html.contains("<b>"));
    }

    @Test
    @DisplayName("搜索应支持时间范围过滤与 highlight 字段")
    void search_timeRangeAndHighlight() throws Exception {
        TestUser a = registerAndLogin("srcha");
        TestUser b = registerAndLogin("srchb");
        ConversationVO conv = becomeFriendsAndOpen(a, b);

        String keyword = "highlight-kw-" + System.nanoTime();
        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(conv.getId());
        dto.setMsgType("text");
        dto.setContent("prefix " + keyword + " suffix");
        chatService.sendMessage(a.userId, dto);

        long now = System.currentTimeMillis();
        List<ChatSearchHitVO> hits = chatService.searchMessages(
                a.userId, keyword, null, conv.getId(), now - 60_000, now + 60_000, 20);
        assertFalse(hits.isEmpty());
        assertNotNull(hits.get(0).getHighlight());
        assertTrue(hits.get(0).getHighlight().contains("<mark>"));

        List<ChatSearchHitVO> empty = chatService.searchMessages(
                a.userId, keyword, null, conv.getId(), now + 3_600_000, now + 7_200_000, 20);
        assertTrue(empty.isEmpty());

        mockMvc.perform(get("/chat/search")
                        .header("Authorization", a.bearer())
                        .param("q", keyword)
                        .param("fromTime", String.valueOf(now - 60_000))
                        .param("toTime", String.valueOf(now + 60_000)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].highlight").exists());
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
