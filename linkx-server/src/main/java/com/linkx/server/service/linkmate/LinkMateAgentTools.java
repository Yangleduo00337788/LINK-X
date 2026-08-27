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
 * 灵伴 Agent 模式：客户端操作工具定义（OpenAI function calling 格式）。
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
        tools.add(functionTool(
                "send_message",
                "向指定或当前聊天会话发送文本消息",
                sendMessageSchema()));
        tools.add(functionTool(
                "create_calendar_event",
                "创建日历日程（需 title 与 date）",
                createCalendarEventSchema()));
        tools.add(functionTool(
                "add_favorite",
                "添加一条收藏（笔记/链接等）",
                addFavoriteSchema()));
        return tools;
    }

    public String agentSystemSuffix() {
        return "当用户希望你帮忙操作 LinkX 客户端（切换页面、打开聊天、搜索、发消息、创建日程、添加收藏等）时，"
                + "请调用提供的工具执行，不要只告诉用户去点哪里。"
                + "若仅需文字回答、无需操作客户端，则正常回复即可。"
                + "一次可调用多个工具；参数中的 nav 必须使用英文键名。"
                + "发消息时 content 必填；创建日程时 title 与 date(YYYY-MM-DD) 必填；未指定会话时可对当前会话发消息。";
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

    private ObjectNode sendMessageSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("目标会话 ID，可留空表示当前会话"));
        properties.set("name", stringField("联系人或群聊名称，用于模糊匹配"));
        properties.set("content", stringField("要发送的文本内容"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("content");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode createCalendarEventSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("title", stringField("日程标题"));
        properties.set("date", stringField("日期 YYYY-MM-DD"));
        properties.set("time", stringField("开始时间 HH:mm，可选"));
        properties.set("endTime", stringField("结束时间 HH:mm，可选"));
        properties.set("color", stringField("展示颜色，可选"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("title");
        required.add("date");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode addFavoriteSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("title", stringField("收藏标题"));
        properties.set("content", stringField("收藏正文，可留空则使用标题"));
        properties.set("type", stringField("类型：note/link/image/file/message，默认 note"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("title");
        schema.set("required", required);
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
