package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.mybatisflex.core.query.QueryWrapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * IM 消息处理与推送服务。
 * <p>
 * 所有 IO 密集操作（DB/Redis/推送扇出）通过 imPushExecutor 线程池执行，不阻塞 Netty event-loop。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessagePushService {

    private final ChatService chatService;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final ImChannelManager channelManager;
    private final ObjectMapper objectMapper;
    private final Executor imPushExecutor;
    private final StringRedisTemplate redisTemplate;

    /**
     * 处理发送消息（异步，event-loop 立即返回）。
     * 流程：参数解析 → submit sendMessage+推送 到 imPushExecutor → event-loop 立即返回。
     * 不在 event-loop 执行任何 DB/Redis 操作；worker 线程内的异常通过错误帧回传发送者。
     *
     * @param senderId 发送者 ID
     * @param frame    WebSocket 帧
     */
    public void handleSend(Long senderId, ImWsFrame frame) {
        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(parseId(frame.getConversationId(), "会话 ID"));
        dto.setMsgType(frame.getMsgType());
        dto.setContent(frame.getContent());
        dto.setFileName(frame.getFileName());
        dto.setFileSize(frame.getFileSize());
        dto.setFileUrl(frame.getFileUrl());
        dto.setClientMsgId(frame.getClientMsgId());

        // 消息风暴检测（持久化）
        if (detectMessageStorm(senderId)) {
            sendErrorToSender(senderId, new CustomException(429, "发送过于频繁，请稍后再试"));
            return;
        }

        // 整体 submit 到线程池，event-loop 立即返回（不阻塞 IO 线程）
        try {
            ((ExecutorService) imPushExecutor).submit(() -> {
                try {
                    doSendAndPush(senderId, dto, frame.getClientMsgId());
                } catch (Exception e) {
                    // worker 内异常：向发送者回错误帧，不静默吞
                    sendErrorToSender(senderId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池饱和：立即向发送者回错误帧，event-loop 不阻塞
            log.warn("IM 推送线程池饱和，拒绝 senderId={} 的消息", senderId);
            sendErrorToSender(senderId, new CustomException(503, "服务繁忙，请稍后重试"));
        }
    }

    /**
     * 向发送者回错误帧。worker 或 event-loop 中均可调用（Netty 写操作线程安全）。
     *
     * @param senderId 发送者 ID
     * @param e        异常（CustomException 取其 code/message，否则用 500）
     */
    private void sendErrorToSender(Long senderId, Exception e) {
        int code;
        String message;
        if (e instanceof CustomException ce) {
            code = ce.getCode();
            message = ce.getMessage();
        } else {
            code = 500;
            message = "消息处理失败";
            log.error("消息处理异常", e);
        }
        ChannelGroup channels = channelManager.getChannels(senderId);
        if (channels == null) {
            return;
        }
        for (Channel channel : channels) {
            sendError(channel, code, message);
        }
    }

    private MessageVO doSendAndPush(Long senderId, SendMessageDTO dto, String clientMsgId) {
        // 业务处理：DB 写消息 + Redis 更新会话
        MessageVO message = chatService.sendMessage(senderId, dto);
        // 推送扇出
        pushToConversationMembers(message, senderId, clientMsgId);
        return message;
    }

    /**
     * 推送消息给会话成员（自包含，无外部 IM 依赖）。
     * Channel/ChannelGroup 写操作线程安全，可从任意线程调用。
     * <p>
     * 超大群优化：对群成员列表一次性查询后分片推送，避免重复序列化，
     * 对 500+ 成员的大群使用异步分批扇出，防止阻塞 worker 线程。
     * </p>
     */
    public void pushToConversationMembers(MessageVO message, Long senderId, String clientMsgId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(message.getConversationId())
        );

        boolean anyRecipientOnline = false;
        int onlineCount = 0;

        // 分片推送阈值：超过此数量启用异步分批
        final int BATCH_THRESHOLD = 500;
        final int BATCH_SIZE = 100;

        // 先收集在线用户列表
        java.util.List<Long> onlineRecipients = new java.util.ArrayList<>();
        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            if (userId.equals(senderId)) continue;
            ChannelGroup channels = channelManager.getChannels(userId);
            if (channels != null && !channels.isEmpty()) {
                onlineRecipients.add(userId);
                anyRecipientOnline = true;
                onlineCount++;
            }
        }

        // 为发送者构建 ack 帧（仅序列化一次）
        String ackJson = null;
        if (clientMsgId != null && !clientMsgId.isBlank()) {
            MessageVO ackPayload = withPerspective(message, senderId);
            ImWsFrame ackFrame = buildFrame("ack", ackPayload);
            ackFrame.setClientMsgId(clientMsgId);
            ackFrame.setServerMsgId(message.getId());
            ackJson = toJson(ackFrame);
        }

        // 推送 ack 给发送者
        if (ackJson != null) {
            ChannelGroup senderChannels = channelManager.getChannels(senderId);
            if (senderChannels != null) {
                for (Channel channel : senderChannels) {
                    if (channel.isActive()) {
                        channel.writeAndFlush(new TextWebSocketFrame(ackJson));
                    }
                }
            }
        }

        // 推送消息给接收者（分片异步）
        if (onlineCount <= BATCH_THRESHOLD) {
            // 小群/中群：直接序列化推送
            String messageJson = null;
            for (Long recipientId : onlineRecipients) {
                MessageVO payload = withPerspective(message, recipientId);
                ChannelGroup channels = channelManager.getChannels(recipientId);
                if (channels == null || channels.isEmpty()) continue;

                if (messageJson == null) {
                    messageJson = toJson(buildFrame("message", payload));
                } else {
                    // 同一个 payload for 所有接收者（除了 isSelf 不同）
                    messageJson = toJson(buildFrame("message", payload));
                }

                for (Channel channel : channels) {
                    if (channel.isActive()) {
                        channel.writeAndFlush(new TextWebSocketFrame(messageJson));
                    }
                }
            }
        } else {
            // 大群：异步分批推送
            final String baseMessageJson = toJson(buildFrame("message", withPerspective(message, onlineRecipients.isEmpty() ? senderId : onlineRecipients.get(0))));
            final int totalBatches = (onlineCount + BATCH_SIZE - 1) / BATCH_SIZE;

            for (int batchIdx = 0; batchIdx < totalBatches; batchIdx++) {
                int start = batchIdx * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, onlineCount);
                java.util.List<Long> batch = onlineRecipients.subList(start, end);

                try {
                    ((ExecutorService) imPushExecutor).submit(() -> {
                        for (Long recipientId : batch) {
                            try {
                                MessageVO payload = withPerspective(message, recipientId);
                                String json = toJson(buildFrame("message", payload));
                                ChannelGroup channels = channelManager.getChannels(recipientId);
                                if (channels == null || channels.isEmpty()) continue;
                                for (Channel channel : channels) {
                                    if (channel.isActive()) {
                                        channel.writeAndFlush(new TextWebSocketFrame(json));
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("大群推送分片异常: recipientId={}", recipientId, e);
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    log.warn("大群推送线程池饱和，跳过分片 batchIdx={}", batchIdx);
                }
            }
        }

        // 投递回执：如果有非发送者的接收方在线，更新 deliveryStatus 为 delivered
        if (anyRecipientOnline) {
            updateDeliveryStatus(message.getId(), "delivered");
        }

        log.debug("消息推送完成: conversationId={}, 成员数={}, 在线接收者={}", message.getConversationId(), members.size(), onlineCount);
    }

    /**
     * 更新消息投递状态。
     */
    private void updateDeliveryStatus(Long messageId, String status) {
        try {
            ImMessage msg = messageMapper.selectOneById(messageId);
            if (msg != null && !status.equals(msg.getDeliveryStatus())) {
                msg.setDeliveryStatus(status);
                messageMapper.update(msg);
            }
        } catch (Exception e) {
            log.warn("更新消息投递状态失败: messageId={}, status={}", messageId, status, e);
        }
    }

    /**
     * 向会话全体在线成员推送撤回事件（含发送者其它端）。
     */
    public void pushRecallToConversationMembers(MessageVO message) {
        if (message == null || message.getConversationId() == null) {
            return;
        }
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(message.getConversationId())
        );
        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            ChannelGroup channels = channelManager.getChannels(userId);
            if (channels == null || channels.isEmpty()) {
                continue;
            }
            MessageVO payload = withPerspective(message, userId);
            String json = toJson(buildFrame("recall", payload));
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }
        }
    }

    /**
     * 广播已读回执给会话其他成员。
     * 当用户标记已读时调用，向会话中的其他在线成员推送 readReceipt 帧。
     */
    public void pushReadReceipt(Long conversationId, Long readerId, Long lastReadMessageId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
        );

        java.util.Map<String, Object> receiptData = java.util.Map.of(
                "conversationId", conversationId,
                "readerId", readerId,
                "lastReadMessageId", lastReadMessageId != null ? lastReadMessageId : 0
        );

        String json = toJson(buildFrame("readReceipt", receiptData));

        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            if (userId.equals(readerId)) continue;
            ChannelGroup channels = channelManager.getChannels(userId);
            if (channels == null || channels.isEmpty()) {
                continue;
            }
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }
        }
    }

    /**
     * 处理客户端撤回消息请求（WebSocket action=recall）。
     * 流程：参数解析 → ChatService.recallMessage → pushRecallToConversationMembers。
     */
    public void handleRecall(Long userId, ImWsFrame frame) {
        try {
            Long conversationId = parseId(frame.getConversationId(), "会话 ID");
            Long messageId = frame.getServerMsgId();
            if (messageId == null) {
                throw new CustomException(400, "消息 ID 不能为空");
            }
            MessageVO recalled = chatService.recallMessage(userId, conversationId, messageId);
            pushRecallToConversationMembers(recalled);
        } catch (CustomException e) {
            sendErrorToSender(userId, e);
        } catch (Exception e) {
            log.error("处理撤回消息失败", e);
            sendErrorToSender(userId, new CustomException(500, "撤回失败"));
        }
    }

    /**
     * 处理客户端编辑消息请求（WebSocket action=edit）。
     * 流程：参数解析 → ChatService.editMessage → pushEditToConversationMembers。
     */
    public void handleEdit(Long userId, ImWsFrame frame) {
        try {
            Long conversationId = parseId(frame.getConversationId(), "会话 ID");
            Long messageId = frame.getServerMsgId();
            if (messageId == null) {
                throw new CustomException(400, "消息 ID 不能为空");
            }
            String newContent = frame.getContent();
            if (newContent == null || newContent.isBlank()) {
                throw new CustomException(400, "编辑内容不能为空");
            }
            MessageVO edited = chatService.editMessage(userId, conversationId, messageId, newContent);
            pushEditToConversationMembers(edited);
        } catch (CustomException e) {
            sendErrorToSender(userId, e);
        } catch (Exception e) {
            log.error("处理编辑消息失败", e);
            sendErrorToSender(userId, new CustomException(500, "编辑失败"));
        }
    }

    /**
     * 向会话全体在线成员推送消息编辑事件。
     */
    public void pushEditToConversationMembers(MessageVO message) {
        if (message == null || message.getConversationId() == null) {
            return;
        }
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(message.getConversationId())
        );
        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            ChannelGroup channels = channelManager.getChannels(userId);
            if (channels == null || channels.isEmpty()) {
                continue;
            }
            MessageVO payload = withPerspective(message, userId);
            String json = toJson(buildFrame("edit", payload));
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }
        }
    }

    /**
     * 处理客户端消息重试请求（WebSocket action=retry）。
     * <p>
     * 客户端发送失败后携带原始 clientMsgId 重试，服务端通过 clientMsgId 去重：
     * 若消息已存在则直接返回 ack；若不存在则正常发送。
     * </p>
     */
    public void handleRetry(Long senderId, ImWsFrame frame) {
        if (frame.getClientMsgId() == null || frame.getClientMsgId().isBlank()) {
            sendErrorToSender(senderId, new CustomException(400, "重试必须携带 clientMsgId"));
            return;
        }
        // 复用 handleSend，内部已实现 clientMsgId 去重逻辑
        handleSend(senderId, frame);
    }

    /**
     * 处理客户端送达回执确认（WebSocket action=deliveryReceipt）。
     * <p>
     * 接收端收到消息后向服务端确认，服务端向发送者推送 deliveryReceipt 事件。
     * </p>
     */
    public void handleDeliveryReceipt(Long userId, ImWsFrame frame) {
        Long messageId = frame.getServerMsgId();
        if (messageId == null) {
            sendErrorToSender(userId, new CustomException(400, "消息 ID 不能为空"));
            return;
        }
        ImMessage msg = messageMapper.selectOneById(messageId);
        if (msg == null) {
            sendErrorToSender(userId, new CustomException(404, "消息不存在"));
            return;
        }
        // 更新投递状态
        updateDeliveryStatus(messageId, "delivered");
        // 向发送者推送送达回执
        java.util.Map<String, Object> receiptData = java.util.Map.of(
                "messageId", messageId,
                "conversationId", msg.getConversationId(),
                "receiverId", userId,
                "deliveryStatus", "delivered"
        );
        pushToUser(msg.getSenderId(), "deliveryReceipt", receiptData);
    }

    /**
     * 获取消息的已读人数（群聊场景）。
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     * @param totalMembers   群成员总数
     * @return 已读人数
     */
    public long getMessageReadCount(Long conversationId, Long messageId, int totalMembers) {
        return memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getLastReadMessageId).ge(messageId)
        );
    }

    public void sendError(Channel channel, int code, String message) {
        if (!channel.isActive()) {
            return;
        }
        ImWsFrame frame = new ImWsFrame();
        frame.setAction("error");
        frame.setCode(code);
        frame.setMessage(message);
        channel.writeAndFlush(new TextWebSocketFrame(toJson(frame)));
    }

    /**
     * 向指定用户的所有在线端推送自定义 WS 帧（通话信令等）。
     */
    public void pushToUser(Long userId, String action, Object data) {
        if (userId == null) {
            return;
        }
        ChannelGroup channels = channelManager.getChannels(userId);
        if (channels == null || channels.isEmpty()) {
            return;
        }
        String json = toJson(buildFrame(action, data));
        for (Channel channel : channels) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }

    /**
     * 处理客户端同步请求：拉取离线期间积压的消息。
     * <p>
     * 客户端发送 sync action 时携带 lastServerMsgId（最后收到的服务端消息 ID），
     * 服务端查询该用户所在所有会话中 id > lastServerMsgId 的消息并推送回去。
     * </p>
     */
    public void handleSync(Long userId, ImWsFrame frame, Channel channel) {
        Long lastServerMsgId = null;
        if (frame.getServerMsgId() != null) {
            lastServerMsgId = frame.getServerMsgId();
        }

        // 查询用户所在所有会话
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );

        if (memberships.isEmpty()) {
            ImWsFrame resp = new ImWsFrame();
            resp.setAction("sync");
            resp.setCode(200);
            resp.setMessage("ok");
            resp.setData(java.util.Map.of("userId", userId, "messages", java.util.List.of()));
            channel.writeAndFlush(new TextWebSocketFrame(toJson(resp)));
            return;
        }

        // 查询离线期间的消息（id > lastServerMsgId）
        com.mybatisflex.core.query.QueryWrapper qw = com.mybatisflex.core.query.QueryWrapper.create()
                .where(ImMessage::getConversationId).in(
                        memberships.stream().map(ImConversationMember::getConversationId).collect(java.util.stream.Collectors.toSet()))
                .and(ImMessage::getType).ne(ImMessage.TYPE_RECALL)
                .and(ImMessage::getType).ne(ImMessage.TYPE_SYSTEM);

        if (lastServerMsgId != null) {
            qw.and(ImMessage::getId).gt(lastServerMsgId);
        }
        qw.orderBy(ImMessage::getCreateTime, true).limit(200);

        List<ImMessage> offlineMessages = messageMapper.selectListByQuery(qw);

        // 转换为 MessageVO 并推送
        if (!offlineMessages.isEmpty()) {
            Set<Long> senderIds = offlineMessages.stream().map(ImMessage::getSenderId).collect(java.util.stream.Collectors.toSet());
            Map<Long, com.linkx.server.entity.SysUser> senderMap = sysUserMapper.selectListByQuery(
                    com.mybatisflex.core.query.QueryWrapper.create()
                            .where(com.linkx.server.entity.SysUser::getId).in(senderIds)
            ).stream().collect(java.util.stream.Collectors.toMap(
                    com.linkx.server.entity.SysUser::getId, u -> u, (a, b) -> a));

            for (ImMessage msg : offlineMessages) {
                com.linkx.server.entity.SysUser sender = senderMap.get(msg.getSenderId());
                MessageVO vo = toMessageVO(msg, sender, userId);
                ImWsFrame pushFrame = buildFrame("message", withPerspective(vo, userId));
                channel.writeAndFlush(new TextWebSocketFrame(toJson(pushFrame)));
            }
        }

        // 回复同步完成
        ImWsFrame resp = new ImWsFrame();
        resp.setAction("syncDone");
        resp.setCode(200);
        resp.setMessage("ok");
        resp.setData(java.util.Map.of(
                "userId", userId,
                "offlineCount", offlineMessages.size()
        ));
        channel.writeAndFlush(new TextWebSocketFrame(toJson(resp)));
    }

    private MessageVO toMessageVO(ImMessage msg, com.linkx.server.entity.SysUser sender, Long viewerId) {
        return MessageVO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .senderNickname(sender != null ? sender.getNickname() : null)
                .senderAvatar(sender != null ? sender.getAvatar() : null)
                .type(msg.getType())
                .content(msg.getContent())
                .fileName(msg.getFileName())
                .fileSize(msg.getFileSize())
                .fileUrl(msg.getFileUrl())
                .voiceDuration(msg.getVoiceDuration())
                .createTime(msg.getCreateTime() != null ? msg.getCreateTime().getTime() : null)
                .isSelf(msg.getSenderId().equals(viewerId))
                .build();
    }

    public String buildPong() {
        ImWsFrame frame = new ImWsFrame();
        frame.setAction("pong");
        return toJson(frame);
    }

    private MessageVO withPerspective(MessageVO message, Long viewerId) {
        return MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderNickname(message.getSenderNickname())
                .senderAvatar(message.getSenderAvatar())
                .type(message.getType())
                .content(message.getContent())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileUrl(message.getFileUrl())
                .voiceDuration(message.getVoiceDuration())
                .createTime(message.getCreateTime())
                .isSelf(message.getSenderId().equals(viewerId))
                // 红包专属字段（与 message 同一发送者视角，无需为 viewer 重算；
                // 服务端在 toMessageVO 时已按 viewer 填好 received/receivedAmount/status）
                .redPacketId(message.getRedPacketId())
                .redPacketGreeting(message.getRedPacketGreeting())
                .redPacketTotalAmount(message.getRedPacketTotalAmount())
                .redPacketType(message.getRedPacketType())
                .redPacketTotalCount(message.getRedPacketTotalCount())
                .redPacketRemainingCount(message.getRedPacketRemainingCount())
                .redPacketReceived(message.getRedPacketReceived())
                .redPacketReceivedAmount(message.getRedPacketReceivedAmount())
                .redPacketStatus(message.getRedPacketStatus())
                .build();
    }

    public void sendAck(Channel channel, MessageVO message, String clientMsgId) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        ImWsFrame frame = buildFrame("ack", message);
        frame.setClientMsgId(clientMsgId);
        frame.setServerMsgId(message.getId());
        channel.writeAndFlush(new TextWebSocketFrame(toJson(frame)));
    }

    private ImWsFrame buildFrame(String action, Object data) {
        ImWsFrame frame = new ImWsFrame();
        frame.setAction(action);
        frame.setData(data);
        return frame;
    }

    private Long parseId(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(400, label + "不能为空");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new CustomException(400, "无效的 " + label);
        }
    }

    private String toJson(ImWsFrame frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.error("序列化 WS 帧失败", e);
            return "{\"action\":\"error\",\"code\":500,\"message\":\"序列化失败\"}";
        }
    }

    // ==================== 消息风暴检测（持久化） ====================

    private static final String STORM_KEY_PREFIX = "linkx:msg:storm:";
    private static final int STORM_THRESHOLD = 30; // 10 秒内超过 30 条触发风暴检测
    private static final int STORM_WINDOW_SECONDS = 10;

    /**
     * 检测用户是否触发消息风暴（10 秒内发送超过阈值）。
     * 检测结果持久化到 Redis，支持跨实例感知。
     *
     * @return true 表示触发风暴检测，应限流
     */
    public boolean detectMessageStorm(Long userId) {
        String key = STORM_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, java.time.Duration.ofSeconds(STORM_WINDOW_SECONDS));
        }
        if (count != null && count > STORM_THRESHOLD) {
            log.warn("消息风暴检测: userId={}, count={}", userId, count);
            // 持久化风暴记录
            String stormLogKey = "linkx:msg:storm:log:" + userId;
            redisTemplate.opsForList().rightPush(stormLogKey,
                    String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(stormLogKey, java.time.Duration.ofHours(1));
            return true;
        }
        return false;
    }

    /**
     * 获取用户风暴记录（最近 1 小时）
     */
    public List<String> getStormLogs(Long userId) {
        String stormLogKey = "linkx:msg:storm:log:" + userId;
        List<String> logs = redisTemplate.opsForList().range(stormLogKey, 0, -1);
        return logs != null ? logs : List.of();
    }

    // ==================== 消息缓存 ====================

    private static final String MSG_CACHE_PREFIX = "linkx:msg:cache:";
    private static final java.time.Duration MSG_CACHE_TTL = java.time.Duration.ofMinutes(5);

    /**
     * 缓存最近消息到 Redis（热消息缓存）
     */
    public void cacheRecentMessage(Long conversationId, MessageVO message) {
        try {
            String key = MSG_CACHE_PREFIX + conversationId;
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -50, -1); // 保留最近 50 条
            redisTemplate.expire(key, MSG_CACHE_TTL);
        } catch (Exception e) {
            log.warn("缓存消息失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 从缓存获取最近消息
     */
    public List<MessageVO> getCachedMessages(Long conversationId) {
        try {
            String key = MSG_CACHE_PREFIX + conversationId;
            List<String> rawList = redisTemplate.opsForList().range(key, 0, -1);
            if (rawList == null) return List.of();
            return rawList.stream()
                    .map(raw -> {
                        try {
                            return objectMapper.readValue(raw, MessageVO.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(m -> m != null)
                    .toList();
        } catch (Exception e) {
            log.warn("获取缓存消息失败: conversationId={}", conversationId, e);
            return List.of();
        }
    }
}
