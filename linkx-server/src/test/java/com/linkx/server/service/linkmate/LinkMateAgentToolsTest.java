package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkMateAgentToolsTest {

    private LinkMateAgentTools tools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tools = new LinkMateAgentTools(objectMapper);
    }

    @Test
    void buildToolsArray_containsAllAgentTools() {
        JsonNode toolsArray = tools.buildToolsArray();
        assertEquals(27, toolsArray.size());

        Set<String> names = new HashSet<>();
        for (JsonNode tool : toolsArray) {
            assertEquals("function", tool.path("type").asText());
            names.add(tool.path("function").path("name").asText());
        }

        assertEquals(
                Set.of(
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
                        "recharge_balance"),
                names);
    }

    @Test
    void sendMessageTool_requiresContent() {
        JsonNode sendMessage = findTool("send_message");
        JsonNode required = sendMessage.path("function").path("parameters").path("required");
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("content", required.get(0).asText());
        assertTrue(sendMessage.path("function").path("parameters").path("properties").has("replyToMessageId"));
        assertTrue(sendMessage.path("function").path("parameters").path("properties").has("mentionNames"));
    }

    @Test
    void createCalendarEventTool_requiresTitleAndDate() {
        JsonNode tool = findTool("create_calendar_event");
        JsonNode required = tool.path("function").path("parameters").path("required");
        assertEquals(2, required.size());
        assertEquals("title", required.get(0).asText());
        assertEquals("date", required.get(1).asText());
    }

    @Test
    void navigateTool_requiresNav() {
        JsonNode tool = findTool("navigate");
        JsonNode required = tool.path("function").path("parameters").path("required");
        assertEquals(1, required.size());
        assertEquals("nav", required.get(0).asText());
        assertTrue(tool.path("function").path("parameters").path("properties").has("settingsTab"));
    }

    @Test
    void agentSystemSuffix_mentionsClientOperationGuidance() {
        String suffix = tools.agentSystemSuffix();
        assertNotNull(suffix);
        assertTrue(suffix.contains("工具"));
        assertTrue(suffix.contains("replyToMessageId"));
        assertFalse(suffix.isBlank());
    }

    private JsonNode findTool(String name) {
        for (JsonNode tool : tools.buildToolsArray()) {
            if (tool.path("function").path("name").asText().equals(name)) {
                return tool;
            }
        }
        throw new AssertionError("tool not found: " + name);
    }
}
