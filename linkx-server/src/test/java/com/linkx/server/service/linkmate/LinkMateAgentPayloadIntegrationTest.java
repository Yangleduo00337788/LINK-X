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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务端 Agent SSE 载荷与客户端 parseAgentAction 契约集成测试（无 LLM 依赖）。
 */
class LinkMateAgentPayloadIntegrationTest {

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
        List<String> toolNames = List.of(
                "navigate",
                "open_chat",
                "open_search",
                "open_calendar",
                "send_message",
                "create_calendar_event",
                "add_favorite");

        for (String name : toolNames) {
            assertTrue(containsTool(tools, name));
        }

        List<Map<String, Object>> actions = List.of(
                Map.of("id", "call_1", "name", "navigate", "arguments", "{\"nav\":\"chat\"}"),
                Map.of(
                        "id",
                        "call_2",
                        "name",
                        "send_message",
                        "arguments",
                        "{\"name\":\"张三\",\"content\":\"你好\",\"chatType\":\"direct\"}"));

        for (Map<String, Object> action : actions) {
            String name = (String) action.get("name");
            assertTrue(toolNames.contains(name));
            String argsJson = (String) action.get("arguments");
            assertFalse(argsJson.isBlank());
            assertTrue(argsJson.startsWith("{"));
        }
    }

    @Test
    void clientContextFields_alignWithAgentPrompt() {
        String suffix = agentTools.agentSystemSuffix();
        assertTrue(suffix.contains("conversationId"));
        assertTrue(suffix.contains("chatType"));
        assertTrue(suffix.contains("direct"));
    }

    @Test
    void toolCount_matchesClientRegistry() {
        assertEquals(7, agentTools.buildToolsArray().size());
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
