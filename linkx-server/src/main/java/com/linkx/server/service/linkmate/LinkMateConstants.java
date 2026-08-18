package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
public final class LinkMateConstants {

    /** 群聊内灵伴消息的虚拟发送者 ID（非真实用户） */
    public static final Long BOT_SENDER_ID = 0L;

    public static final String BOT_NICKNAME = "灵伴 LinkMate";

    /** 群聊内小助手展示名（@ 与消息发送者昵称） */
    public static final String GROUP_ASSISTANT_NICKNAME = "群聊小助手";

    /** LLM 返回此标记表示跳过回复（智能总结等场景） */
    public static final String SKIP_REPLY_MARKER = "[SKIP]";

    /** 智能总结消息前缀 */
    public static final String SUMMARY_MESSAGE_PREFIX = "【智能总结】\n";

    /** 群聊主动发言默认关注话题 */
    public static final String DEFAULT_GROUP_AI_INTEREST_TOPICS = "群聊讨论的相关话题";

    /** 群聊智能总结默认指令 */
    public static final String DEFAULT_GROUP_AI_SUMMARY_INSTRUCTION = "提炼讨论要点、待办与结论";

    private LinkMateConstants() {
    }
}
