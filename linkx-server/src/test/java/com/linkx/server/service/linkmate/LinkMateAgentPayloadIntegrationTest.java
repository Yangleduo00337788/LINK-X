package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务端 Agent SSE 载荷与客户端 parseAgentAction 契约集成测试（无 LLM 依赖）。
 */
class LinkMateAgentPayloadIntegrationTest {

    private static final Set<String> REGISTERED_TOOL_NAMES = Set.of(
            "navigate",
            "open_linkmate",
            "open_chat",
            "open_search",
            "open_calendar",
            "open_contacts",
            "send_message",
            "add_friend",
            "handle_friend_request",
            "handle_group_invitation",
            "create_calendar_event",
            "update_calendar_event",
            "delete_calendar_event",
            "add_favorite",
            "update_favorite",
            "delete_favorite",
            "tag_favorite",
            "create_folder",
            "upload_file",
            "publish_moment",
            "publish_short_video",
            "send_red_packet",
            "start_call",
            "create_group",
            "add_group_members",
            "update_setting",
            "recharge_balance");

    private LinkMateAgentTools agentTools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agentTools = new LinkMateAgentTools(objectMapper);
    }

    @Test
    void donePayload_actionsMatchRegisteredTools() {
        ArrayNode tools = agentTools.buildToolsArray();
        for (String name : REGISTERED_TOOL_NAMES) {
            assertTrue(containsTool(tools, name), "missing tool: " + name);
        }

        List<Map<String, Object>> actions = List.of(
                Map.of("id", "call_1", "name", "navigate", "arguments", "{\"nav\":\"chat\"}"),
                Map.of(
                        "id",
                        "call_2",
                        "name",
                        "send_message",
                        "arguments",
                        "{\"name\":\"张三\",\"content\":\"你好\",\"chatType\":\"direct\"}"),
                Map.of("id", "call_3", "name", "open_linkmate", "arguments", "{}"),
                Map.of(
                        "id",
                        "call_4",
                        "name",
                        "open_contacts",
                        "arguments",
                        "{\"view\":\"friend-notifs\"}"),
                Map.of(
                        "id",
                        "call_5",
                        "name",
                        "update_calendar_event",
                        "arguments",
                        "{\"eventId\":\"e1\",\"title\":\"新周会\"}"));

        for (Map<String, Object> action : actions) {
            String name = (String) action.get("name");
            assertTrue(REGISTERED_TOOL_NAMES.contains(name));
            String argsJson = (String) action.get("arguments");
            assertFalse(argsJson.isBlank());
            assertTrue(argsJson.startsWith("{") || argsJson.equals("{}"));
        }
    }

    @Test
    void clientContextFields_alignWithAgentPrompt() {
        String suffix = agentTools.agentSystemSuffix();
        assertTrue(suffix.contains("chatType"));
        assertTrue(suffix.contains("direct"));
        assertTrue(suffix.contains("replyToMessageId"));
        assertTrue(suffix.contains("mentionNames"));
        assertTrue(suffix.contains("moments"));
        assertTrue(suffix.contains("linkmate"));
        assertTrue(suffix.contains("clientContext.todayDate"));
    }

    @Test
    void toolCount_matchesClientRegistry() {
        assertEquals(REGISTERED_TOOL_NAMES.size(), agentTools.buildToolsArray().size());
        assertEquals(27, agentTools.buildToolsArray().size());
    }

    private boolean containsTool(ArrayNode tools, String name) {
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i).path("function").path("name").asText().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
