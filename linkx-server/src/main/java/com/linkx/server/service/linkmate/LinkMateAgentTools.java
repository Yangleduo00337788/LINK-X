package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 灵伴 Agent 模式：Phase 1 只读客户端操作工具定义（OpenAI function calling 格式）。
 */
@Component
@RequiredArgsConstructor
public class LinkMateAgentTools {

    private final ObjectMapper objectMapper;

    public ArrayNode buildToolsArray() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(functionTool(
                "navigate",
                "切换 LinkX 主导航页面",
                stringPropertySchema("nav", "目标页面：chat/contacts/favorites/files/calendar/moments/shortVideo/balance/linkmate/settings", true)));
        tools.add(functionTool(
                "open_chat",
                "打开指定聊天会话（按会话 ID 或联系人/群名称模糊匹配）",
                openChatSchema()));
        tools.add(functionTool(
                "open_search",
                "打开综合搜索并可选填入关键词",
                stringPropertySchema("keyword", "搜索关键词，可留空", false)));
        tools.add(functionTool(
                "open_calendar",
                "打开日历页面",
                emptyObjectSchema()));
        return tools;
    }

    public String agentSystemSuffix() {
        return "当用户希望你帮忙操作 LinkX 客户端（切换页面、打开聊天、搜索、查看日历等）时，"
                + "请调用提供的工具执行，不要只告诉用户去点哪里。"
                + "若仅需文字回答、无需操作客户端，则正常回复即可。"
                + "一次可调用多个工具；参数中的 nav 必须使用英文键名。";
    }

    private ObjectNode emptyObjectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    private ObjectNode openChatSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("会话 ID，已知时优先使用"));
        properties.set("name", stringField("联系人或群聊名称，用于模糊匹配"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode stringPropertySchema(String name, String description, boolean required) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set(name, stringField(description));
        schema.set("properties", properties);
        if (required) {
            ArrayNode requiredArr = objectMapper.createArrayNode();
            requiredArr.add(name);
            schema.set("required", requiredArr);
        }
        return schema;
    }

    private ObjectNode stringField(String description) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    private ObjectNode functionTool(String name, String description, ObjectNode parameters) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", name);
        fn.put("description", description);
        fn.set("parameters", parameters);
        return tool;
    }
}
