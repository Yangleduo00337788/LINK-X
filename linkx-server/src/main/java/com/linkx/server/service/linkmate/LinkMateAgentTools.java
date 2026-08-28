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
                "切换 LinkX 主导航页面（moments/shortVideo/linkmate/settings 会走与侧栏一致的打开逻辑）",
                navigateSchema()));
        tools.add(functionTool(
                "open_linkmate",
                "打开灵伴扩展面板",
                emptyObjectSchema()));
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
                "open_contacts",
                "打开通讯录，可进入好友/群通知列表",
                openContactsSchema()));
        tools.add(functionTool(
                "send_message",
                "向指定或当前聊天会话发送文本消息，支持回复与 @ 提及",
                sendMessageSchema()));
        tools.add(functionTool(
                "add_friend",
                "通过用户名发送好友申请",
                addFriendSchema()));
        tools.add(functionTool(
                "handle_friend_request",
                "同意或拒绝好友申请",
                handleFriendRequestSchema()));
        tools.add(functionTool(
                "handle_group_invitation",
                "同意或拒绝群邀请",
                handleGroupInvitationSchema()));
        tools.add(functionTool(
                "create_calendar_event",
                "创建日历日程（需 title 与 date）",
                createCalendarEventSchema()));
        tools.add(functionTool(
                "update_calendar_event",
                "更新已有日历日程",
                updateCalendarEventSchema()));
        tools.add(functionTool(
                "delete_calendar_event",
                "删除日历日程",
                deleteCalendarEventSchema()));
        tools.add(functionTool(
                "add_favorite",
                "添加一条收藏（笔记/链接等）",
                addFavoriteSchema()));
        tools.add(functionTool(
                "update_favorite",
                "更新收藏标题或内容",
                updateFavoriteSchema()));
        tools.add(functionTool(
                "delete_favorite",
                "删除收藏",
                deleteFavoriteSchema()));
        tools.add(functionTool(
                "tag_favorite",
                "为收藏打标签",
                tagFavoriteSchema()));
        tools.add(functionTool(
                "create_folder",
                "在文件页创建文件夹",
                stringPropertySchema("name", "文件夹名称", true)));
        tools.add(functionTool(
                "upload_file",
                "打开文件页准备上传（需用户在界面选择文件）",
                emptyObjectSchema()));
        tools.add(functionTool(
                "publish_moment",
                "发布朋友圈文字动态",
                publishMomentSchema()));
        tools.add(functionTool(
                "publish_short_video",
                "打开短视频发布面板（视频文件需用户手动选择）",
                publishShortVideoSchema()));
        tools.add(functionTool(
                "send_red_packet",
                "向当前或指定会话发送红包",
                sendRedPacketSchema()));
        tools.add(functionTool(
                "start_call",
                "向好友发起语音或视频通话",
                startCallSchema()));
        tools.add(functionTool(
                "create_group",
                "创建群聊并邀请好友",
                createGroupSchema()));
        tools.add(functionTool(
                "add_group_members",
                "向已有群聊添加成员",
                addGroupMembersSchema()));
        tools.add(functionTool(
                "update_setting",
                "修改应用设置项（如 language、notifyChat 等）",
                updateSettingSchema()));
        tools.add(functionTool(
                "recharge_balance",
                "为账户充值余额",
                rechargeBalanceSchema()));
        return tools;
    }

    public String agentSystemSuffix() {
        return "当用户希望你帮忙操作 LinkX 客户端（切换页面、打开聊天、搜索、发消息、通讯录、日历、收藏、文件、朋友圈、红包、通话、设置等）时，"
                + "请调用提供的工具执行，不要只告诉用户去点哪里。"
                + "若仅需文字回答、无需操作客户端，则正常回复即可。"
                + "一次可调用多个工具；参数中的 nav 必须使用英文键名。"
                + "发消息时 content 必填，可用 replyToMessageId 回复某条消息，mentionNames 填要 @ 的好友昵称数组；"
                + "创建/更新日程时 date 必须为 YYYY-MM-DD，请根据 clientContext.todayDate 计算相对日期。"
                + "给某位好友发消息时：name 填该好友昵称/备注，chatType 用 direct；"
                + "navigate 到 moments/shortVideo/linkmate/settings 时会自动展开扩展坞或打开设置子页。"
                + "发红包、充值、建群、通话等写操作仅在用户明确授权时使用。";
    }

    private ObjectNode emptyObjectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    private ObjectNode navigateSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("nav", stringField(
                "目标页面：chat/contacts/favorites/files/calendar/moments/shortVideo/balance/linkmate/settings"));
        properties.set("settingsTab", stringField(
                "nav=settings 时可选子页：account/general/notifications/privacy/chat/files/shortcuts/appearance/about"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("nav");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode openChatSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("会话 ID，已知时优先使用"));
        properties.set("name", stringField("联系人或群聊名称；给好友发消息填好友名，不要填群名"));
        properties.set("chatType", stringField("会话类型：direct=好友单聊，group=群聊；给好友发消息时用 direct"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode openContactsSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("view", stringField("可选：default/friend-notifs/group-notifs"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode sendMessageSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("目标会话 ID，可留空表示当前会话"));
        properties.set("name", stringField("好友昵称/备注或群名；给某位好友发消息时填该好友名"));
        properties.set("chatType", stringField("direct=好友单聊，group=群聊；默认给好友发消息用 direct"));
        properties.set("content", stringField("要发送的文本内容"));
        properties.set("replyToMessageId", stringField("要回复的消息 ID，可选"));
        properties.set("mentionNames", stringField("要 @ 的好友昵称，多个用逗号分隔，可选"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("content");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode addFriendSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("username", stringField("对方登录用户名"));
        properties.set("message", stringField("验证消息，可选"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("username");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode handleFriendRequestSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("requestId", stringField("好友申请 ID，已知时优先"));
        properties.set("fromName", stringField("申请人昵称，无 requestId 时用于匹配"));
        properties.set("action", stringField("accept 或 reject"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("action");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode handleGroupInvitationSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("invitationId", stringField("群邀请 ID，已知时优先"));
        properties.set("groupName", stringField("群名称，无 invitationId 时用于匹配"));
        properties.set("action", stringField("accept 或 reject"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("action");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode createCalendarEventSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("title", stringField("日程标题"));
        properties.set("date", stringField("日期 YYYY-MM-DD；相对日期请按 clientContext.todayDate 换算"));
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

    private ObjectNode updateCalendarEventSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("eventId", stringField("日程 ID，已知时优先"));
        properties.set("title", stringField("原标题，用于匹配日程"));
        properties.set("date", stringField("原日期 YYYY-MM-DD，用于匹配或更新"));
        properties.set("time", stringField("新开始时间 HH:mm，可选"));
        properties.set("endTime", stringField("新结束时间 HH:mm，可选"));
        properties.set("color", stringField("新颜色，可选"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode deleteCalendarEventSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("eventId", stringField("日程 ID，已知时优先"));
        properties.set("title", stringField("日程标题，用于匹配"));
        properties.set("date", stringField("日期 YYYY-MM-DD，用于匹配"));
        schema.set("properties", properties);
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

    private ObjectNode updateFavoriteSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("favoriteId", stringField("收藏 ID，已知时优先"));
        properties.set("title", stringField("收藏标题，用于匹配"));
        properties.set("content", stringField("新正文，可选"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode deleteFavoriteSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("favoriteId", stringField("收藏 ID，已知时优先"));
        properties.set("title", stringField("收藏标题，用于匹配"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode tagFavoriteSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("favoriteId", stringField("收藏 ID，已知时优先"));
        properties.set("title", stringField("收藏标题，用于匹配"));
        properties.set("tags", stringField("标签名，多个用逗号分隔"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("tags");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode publishMomentSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("content", stringField("朋友圈文字内容"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("content");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode publishShortVideoSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("description", stringField("视频描述，可选；视频文件需用户在界面选择"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode sendRedPacketSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("会话 ID，可留空表示当前会话"));
        properties.set("name", stringField("好友或群名称"));
        properties.set("chatType", stringField("direct 或 group"));
        properties.set("amount", stringField("红包总金额，单位元"));
        properties.set("totalCount", stringField("红包个数，默认 1"));
        properties.set("type", stringField("normal 或 lucky，默认 normal"));
        properties.set("greeting", stringField("祝福语，可选"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("amount");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode startCallSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("callType", stringField("voice 或 video"));
        properties.set("conversationId", stringField("好友会话 ID"));
        properties.set("name", stringField("好友昵称/备注"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("callType");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode createGroupSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("groupName", stringField("群名称，可选"));
        properties.set("memberNames", stringField("初始成员昵称，多个用逗号分隔"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("memberNames");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode addGroupMembersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("conversationId", stringField("群会话 ID"));
        properties.set("name", stringField("群名称，用于匹配"));
        properties.set("memberNames", stringField("要添加的好友昵称，多个用逗号分隔"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("memberNames");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode updateSettingSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("key", stringField("设置键，如 language、notifyChat、privacyShowOnline"));
        properties.set("value", stringField("设置值，布尔用 true/false"));
        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("key");
        required.add("value");
        schema.set("required", required);
        return schema;
    }

    private ObjectNode rechargeBalanceSchema() {
        return stringPropertySchema("amount", "充值金额（元）", true);
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
