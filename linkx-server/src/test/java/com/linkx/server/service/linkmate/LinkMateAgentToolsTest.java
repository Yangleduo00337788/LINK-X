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
    void buildToolsArray_containsAllSevenAgentTools() {
        JsonNode toolsArray = tools.buildToolsArray();
        assertEquals(7, toolsArray.size());

        Set<String> names = new HashSet<>();
        for (JsonNode tool : toolsArray) {
            assertEquals("function", tool.path("type").asText());
            names.add(tool.path("function").path("name").asText());
        }

        assertEquals(
                Set.of(
                        "navigate",
                        "open_chat",
                        "open_search",
                        "open_calendar",
                        "send_message",
                        "create_calendar_event",
                        "add_favorite"),
                names);
    }

    @Test
    void sendMessageTool_requiresContent() {
        JsonNode sendMessage = findTool("send_message");
        JsonNode required = sendMessage.path("function").path("parameters").path("required");
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("content", required.get(0).asText());
        assertTrue(sendMessage.path("function").path("parameters").path("properties").has("name"));
        assertTrue(sendMessage.path("function").path("parameters").path("properties").has("chatType"));
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
    }

    @Test
    void agentSystemSuffix_mentionsClientOperationGuidance() {
        String suffix = tools.agentSystemSuffix();
        assertNotNull(suffix);
        assertTrue(suffix.contains("工具"));
        assertTrue(suffix.contains("conversationId"));
        assertTrue(suffix.contains("chatType"));
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
