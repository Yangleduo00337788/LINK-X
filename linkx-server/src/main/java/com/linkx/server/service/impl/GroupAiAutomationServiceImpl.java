package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.GroupAiAutomationService;
import com.linkx.server.service.linkmate.LinkMateConstants;
import com.linkx.server.service.linkmate.LinkMateLlmClient;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmMessage;
import com.linkx.server.service.linkmate.LinkMateLlmClient.LlmResult;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群聊 AI 自动化：用户发言即时回复 + 手动智能总结。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAiAutomationServiceImpl implements GroupAiAutomationService {

    private static final int CONTEXT_LIMIT = 30;
    private static final String SKIP_MARKER = "[SKIP]";
    private static final String SUMMARY_PREFIX = "【智能总结】\n";
    /** 连发消息合并：静默这么久后才回复一次 */
    private static final long PROACTIVE_DEBOUNCE_MS = Duration.ofSeconds(4).toMillis();
    /** 同一群内两次主动回复的最小间隔 */
    private static final long PROACTIVE_COOLDOWN_MS = Duration.ofSeconds(60).toMillis();
    private static final long PROACTIVE_QUIET_POLL_MS = 500L;
    private static final String PROACTIVE_PENDING_KEY = "group_ai:proactive:pending:";
    private static final String PROACTIVE_ACTIVITY_KEY = "group_ai:proactive:activity:";
    private static final String PROACTIVE_SCHEDULER_KEY = "group_ai:proactive:scheduler:";
    private static final Duration PROACTIVE_REDIS_TTL = Duration.ofMinutes(5);

    private final LinkxProperties linkxProperties;
    private final ImConversationMapper conversationMapper;
    private final ImMessageRepository imMessageRepository;
    private final SysUserMapper sysUserMapper;
    private final LinkMateLlmClient llmClient;
    private final ChatService chatService;
    private final ImMessagePushService imMessagePushService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("linkmateAutomationExecutor")
    public void onGroupUserMessage(Long conversationId, ImMessage message) {
        if (conversationId == null || message == null) {
            return;
        }
        if (!ImMessage.TYPE_TEXT.equals(message.getType())) {
            return;
        }
        if (LinkMateConstants.BOT_SENDER_ID.equals(message.getSenderId())) {
            return;
        }
        if (!StringUtils.hasText(message.getContent())) {
            return;
        }
        if (!isLinkmateGloballyEnabled()) {
            return;
        }

        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (!isGroupAiEligible(group)) {
            return;
        }
        if (!Integer.valueOf(1).equals(group.getGroupAiProactiveEnabled())) {
            return;
        }

        long now = System.currentTimeMillis();
        String pendingKey = PROACTIVE_PENDING_KEY + conversationId;
        String activityKey = PROACTIVE_ACTIVITY_KEY + conversationId;
        String schedulerKey = PROACTIVE_SCHEDULER_KEY + conversationId;

        redisTemplate.opsForValue().set(pendingKey, String.valueOf(message.getId()), PROACTIVE_REDIS_TTL);
        redisTemplate.opsForValue().set(activityKey, String.valueOf(now), PROACTIVE_REDIS_TTL);

        Boolean scheduled = redisTemplate.opsForValue().setIfAbsent(schedulerKey, "1", PROACTIVE_REDIS_TTL);
        if (!Boolean.TRUE.equals(scheduled)) {
            return;
        }

        try {
            waitForQuietPeriod(activityKey);
            String pendingId = redisTemplate.opsForValue().get(pendingKey);
            if (!StringUtils.hasText(pendingId)) {
                return;
            }
            ImMessage pending = imMessageRepository.selectOneById(Long.parseLong(pendingId));
            if (pending == null || !conversationId.equals(pending.getConversationId())) {
                return;
            }

            group = conversationMapper.selectOneById(conversationId);
            if (!isGroupAiEligible(group) || !Integer.valueOf(1).equals(group.getGroupAiProactiveEnabled())) {
                return;
            }
            Date lastProactive = group.getGroupAiLastProactiveAt();
            if (lastProactive != null
                    && System.currentTimeMillis() - lastProactive.getTime() < PROACTIVE_COOLDOWN_MS) {
                return;
            }

            processProactiveReply(conversationId, pending);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("群聊主动发言失败 conversationId={}", conversationId, ex);
        } finally {
            redisTemplate.delete(schedulerKey);
        }
    }

    /**
     * 等待群聊在 debounce 窗口内不再有新消息（连发合并为一次回复）。
     */
    private void waitForQuietPeriod(String activityKey) throws InterruptedException {
        while (true) {
            Thread.sleep(PROACTIVE_QUIET_POLL_MS);
            String ts = redisTemplate.opsForValue().get(activityKey);
            if (!StringUtils.hasText(ts)) {
                return;
            }
            long lastActivity = Long.parseLong(ts);
            if (System.currentTimeMillis() - lastActivity >= PROACTIVE_DEBOUNCE_MS) {
                return;
            }
        }
    }

    @Override
    public MessageVO triggerSmartSummary(Long userId, Long conversationId) {
        if (!isLinkmateGloballyEnabled()) {
            throw new CustomException(503, "灵伴服务未启用");
        }
        chatService.assertConversationMember(userId, conversationId);
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        if (!isGroupAiEligible(group)) {
            throw new CustomException(403, "本群已关闭灵伴接入");
        }
        if (!Integer.valueOf(1).equals(group.getGroupAiSmartSummaryEnabled())) {
            throw new CustomException(403, "本群未开启智能总结");
        }

        List<ImMessage> messages = loadRecentMessages(group.getId(), CONTEXT_LIMIT);
        long userMsgCount = messages.stream()
                .filter(m -> !LinkMateConstants.BOT_SENDER_ID.equals(m.getSenderId()))
                .count();
        if (userMsgCount < 1) {
            throw new CustomException(400, "暂无可总结的消息");
        }

        String summary = generateSummary(group, messages);
        if (!StringUtils.hasText(summary) || isSkipReply(summary)) {
            throw new CustomException(400, "当前消息不足以生成总结");
        }

        String content = summary.trim().startsWith("【")
                ? summary.trim()
                : SUMMARY_PREFIX + summary.trim();
        MessageVO vo = postAndPushBotMessage(group, content);

        ImMessage latest = messages.get(messages.size() - 1);
        group.setGroupAiLastSummaryAt(new Date());
        group.setGroupAiLastSummaryMsgId(latest.getId());
        conversationMapper.update(group);
        return vo;
    }

    private void processProactiveReply(Long conversationId, ImMessage message) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (!isGroupAiEligible(group) || !Integer.valueOf(1).equals(group.getGroupAiProactiveEnabled())) {
            return;
        }

        String reply = generateProactiveReply(group, message);
        if (!StringUtils.hasText(reply)) {
            return;
        }

        postAndPushBotMessage(group, reply.trim());
        group.setGroupAiLastProactiveAt(new Date());
        conversationMapper.update(group);
    }

    private String generateProactiveReply(ImConversation group, ImMessage triggerMessage) {
        String topics = StringUtils.hasText(group.getGroupAiInterestTopics())
                ? group.getGroupAiInterestTopics().trim()
                : "群聊讨论的相关话题";
        String groupName = StringUtils.hasText(group.getName()) ? group.getName().trim() : "群聊";
        String userText = triggerMessage.getContent().trim();

        SysUser sender = triggerMessage.getSenderId() != null
                ? sysUserMapper.selectOneById(triggerMessage.getSenderId())
                : null;
        String senderName = sender != null && StringUtils.hasText(sender.getNickname())
                ? sender.getNickname()
                : (sender != null && StringUtils.hasText(sender.getUsername()) ? sender.getUsername() : "用户");

        StringBuilder system = new StringBuilder();
        system.append("你是「灵伴」（LinkMate），正在企业 IM 群聊「").append(groupName).append("」中回复成员消息。");
        system.append("群主/管理员已开启主动发言，关注话题：").append(topics).append("。");
        system.append("请结合最近群聊上下文，对成员「").append(senderName).append("」的最新发言给出自然、简洁、有帮助的回复；");
        system.append("使用与用户相同的语言，不要 @ 任何人，不要加引号或前缀。");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", system.toString()));
        messages.add(new LlmMessage("user",
                "最近群消息：\n" + buildRecentMessagesContext(group.getId(), null)
                        + "\n\n请回复「" + senderName + "」刚才说的：\n" + userText));
        return callLlm(messages);
    }

    private String generateSummary(ImConversation group, List<ImMessage> scopedMessages) {
        String instruction = StringUtils.hasText(group.getGroupAiSummaryInstruction())
                ? group.getGroupAiSummaryInstruction().trim()
                : "提炼讨论要点、待办与结论";
        String groupName = StringUtils.hasText(group.getName()) ? group.getName().trim() : "群聊";

        StringBuilder system = new StringBuilder();
        system.append("你是「灵伴」（LinkMate），请为群聊「").append(groupName).append("」生成智能总结。");
        system.append("总结要求：").append(instruction).append("。");
        system.append("使用简洁条目或短段落，保留关键决策与待办，避免逐条复述闲聊。");
        system.append("若消息过少或没有可总结内容，只回复 ").append(SKIP_MARKER).append("。");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", system.toString()));
        messages.add(new LlmMessage("user", "待总结消息：\n" + buildMessagesContext(scopedMessages)));
        return callLlm(messages);
    }

    private String callLlm(List<LlmMessage> messages) {
        try {
            LlmResult result = llmClient.chat(messages, false);
            return result != null ? result.content() : null;
        } catch (CustomException ex) {
            log.debug("群聊 AI LLM 调用被拒绝: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("群聊 AI LLM 调用失败", ex);
            return null;
        }
    }

    private MessageVO postAndPushBotMessage(ImConversation group, String content) {
        MessageVO vo = chatService.postLinkMateImMessage(group.getId(), content);
        imMessagePushService.pushToConversationMembers(vo, LinkMateConstants.BOT_SENDER_ID, null);
        return vo;
    }

    private List<ImMessage> loadRecentMessages(Long conversationId, int limit) {
        List<ImMessage> messages = imMessageRepository.selectListByQuery(
                QueryWrapper.create()
                        .eq("conversation_id", conversationId)
                        .ne("type", ImMessage.TYPE_SYSTEM)
                        .ne("type", ImMessage.TYPE_RECALL)
                        .orderBy("id", false)
                        .limit(limit)
        );
        messages.sort(Comparator.comparing(ImMessage::getId));
        return messages;
    }

    private String buildRecentMessagesContext(Long conversationId, Long sinceId) {
        List<ImMessage> messages = loadMessagesSince(conversationId, sinceId);
        if (messages.size() > CONTEXT_LIMIT) {
            messages = messages.subList(messages.size() - CONTEXT_LIMIT, messages.size());
        }
        return buildMessagesContext(messages);
    }

    private List<ImMessage> loadMessagesSince(Long conversationId, Long sinceId) {
        QueryWrapper query = QueryWrapper.create()
                .eq("conversation_id", conversationId)
                .ne("type", ImMessage.TYPE_SYSTEM)
                .ne("type", ImMessage.TYPE_RECALL)
                .orderBy("id", true);
        if (sinceId != null) {
            query.gt("id", sinceId);
        }
        return imMessageRepository.selectListByQuery(query);
    }

    private String buildMessagesContext(List<ImMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "（暂无消息）";
        }
        Set<Long> senderIds = messages.stream()
                .map(ImMessage::getSenderId)
                .filter(id -> id != null && !LinkMateConstants.BOT_SENDER_ID.equals(id))
                .collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = senderIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectListByQuery(QueryWrapper.create().in("id", senderIds))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        for (ImMessage msg : messages) {
            String preview = formatMessagePreview(msg);
            if (!StringUtils.hasText(preview)) {
                continue;
            }
            String senderName;
            if (LinkMateConstants.BOT_SENDER_ID.equals(msg.getSenderId())) {
                senderName = LinkMateConstants.BOT_NICKNAME;
            } else {
                SysUser sender = senderMap.get(msg.getSenderId());
                senderName = sender != null && StringUtils.hasText(sender.getNickname())
                        ? sender.getNickname()
                        : (sender != null && StringUtils.hasText(sender.getUsername())
                        ? sender.getUsername() : "用户");
            }
            sb.append(senderName).append("：").append(preview.trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatMessagePreview(ImMessage msg) {
        if (msg == null) {
            return null;
        }
        if (ImMessage.TYPE_TEXT.equals(msg.getType())) {
            return StringUtils.hasText(msg.getContent()) ? msg.getContent().trim() : null;
        }
        if (ImMessage.TYPE_IMAGE.equals(msg.getType())) {
            return "[图片]";
        }
        if (ImMessage.TYPE_FILE.equals(msg.getType())) {
            return StringUtils.hasText(msg.getFileName()) ? "[文件] " + msg.getFileName().trim() : "[文件]";
        }
        if (ImMessage.TYPE_VOICE.equals(msg.getType())) {
            return "[语音]";
        }
        if (ImMessage.TYPE_LOCATION.equals(msg.getType())) {
            return "[位置]";
        }
        return StringUtils.hasText(msg.getContent()) ? msg.getContent().trim() : null;
    }

    private boolean isGroupAiEligible(ImConversation group) {
        return group != null
                && group.getType() == ImConversation.TYPE_GROUP
                && (group.getLinkmateEnabled() == null || group.getLinkmateEnabled() == 1);
    }

    private boolean isLinkmateGloballyEnabled() {
        LinkxProperties.LinkMate cfg = linkxProperties.getLinkmate();
        return cfg.isEnabled() && StringUtils.hasText(cfg.getApiKey());
    }

    private static boolean isSkipReply(String reply) {
        String trimmed = reply.trim();
        return SKIP_MARKER.equals(trimmed) || trimmed.startsWith(SKIP_MARKER);
    }
}
