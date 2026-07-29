package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.MessageStormService;
import com.linkx.server.service.PresenceService;
import com.mybatisflex.core.query.QueryWrapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * IM 消息处理与推送服务。
 * <p>
 * 所有 IO 密集操作（DB/Redis/推送扇出）通过 imPushExecutor / imFanoutExecutor 执行，不阻塞 Netty event-loop。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessagePushService {

    /** 跨实例 IM 帧投递 Redis Stream（替代 Pub/Sub，支持断线续读） */
    public static final String CLUSTER_PUSH_STREAM = "linkx:im:push:stream";
    /** @deprecated 兼容旧常量名，请使用 {@link #CLUSTER_PUSH_STREAM} */
    @Deprecated
    public static final String CLUSTER_PUSH_CHANNEL = CLUSTER_PUSH_STREAM;
    private static final long CLUSTER_PUSH_STREAM_MAXLEN = 10_000L;

    private final ChatService chatService;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final ImChannelManager channelManager;
    private final ObjectMapper objectMapper;
    @Qualifier("imPushExecutor")
    private final Executor imPushExecutor;
    @Qualifier("imFanoutExecutor")
    private final Executor imFanoutExecutor;
    private final StringRedisTemplate redisTemplate;
    private final MessageStormService messageStormService;
    private final PresenceService presenceService;
    private final LinkxProperties linkxProperties;

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
        dto.setVoiceDuration(frame.getVoiceDuration());
        dto.setClientMsgId(frame.getClientMsgId());

        // 风暴检测 + 发送均在 worker 内执行，避免 event-loop 上做 Redis/DB
        try {
            ((ExecutorService) imPushExecutor).submit(() -> {
                try {
                    if (messageStormService.checkAndRecordUserStorm(senderId)) {
                        sendErrorToSender(senderId, new CustomException(429, "发送过于频繁，请稍后再试"));
                        return;
                    }
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

        // 为发送者构建 ack 帧（仅序列化一次）
        String ackJson = null;
        if (clientMsgId != null && !clientMsgId.isBlank()) {
            MessageVO ackPayload = withPerspective(message, senderId);
            ImWsFrame ackFrame = buildFrame("ack", ackPayload);
            ackFrame.setClientMsgId(clientMsgId);
            ackFrame.setServerMsgId(message.getId());
            ackJson = toJson(ackFrame);
        }

        if (ackJson != null && senderId != null) {
            pushFrameToUser(senderId, ackJson, true);
        }

        java.util.List<Long> recipients = new java.util.ArrayList<>();
        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            if (senderId != null && userId.equals(senderId)) {
                continue;
            }
            recipients.add(userId);
        }

        boolean anyRecipientOnline = false;
        final int BATCH_THRESHOLD = 500;
        final int BATCH_SIZE = 100;
        int recipientCount = recipients.size();

        if (recipientCount <= BATCH_THRESHOLD) {
            for (Long recipientId : recipients) {
                if (presenceService.isOnline(recipientId)) {
                    anyRecipientOnline = true;
                }
                MessageVO payload = withPerspective(message, recipientId);
                pushFrameToUser(recipientId, toJson(buildFrame("message", payload)), true);
            }
        } else {
            for (Long recipientId : recipients) {
                if (presenceService.isOnline(recipientId)) {
                    anyRecipientOnline = true;
                }
            }
            final int totalBatches = (recipientCount + BATCH_SIZE - 1) / BATCH_SIZE;
            for (int batchIdx = 0; batchIdx < totalBatches; batchIdx++) {
                int start = batchIdx * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, recipientCount);
                java.util.List<Long> batch = recipients.subList(start, end);
                try {
                    ((ExecutorService) imFanoutExecutor).submit(() -> {
                        for (Long recipientId : batch) {
                            try {
                                MessageVO payload = withPerspective(message, recipientId);
                                pushFrameToUser(recipientId, toJson(buildFrame("message", payload)), true);
                            } catch (Exception e) {
                                log.warn("大群推送分片异常: recipientId={}", recipientId, e);
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    log.warn("大群扇出线程池饱和，跳过分片 batchIdx={}", batchIdx);
                }
            }
        }

        // 投递回执：集群视角有接收方在线则通知发送方
        if (anyRecipientOnline) {
            updateDeliveryStatus(message.getId(), "delivered");
            if (message.getSenderId() != null) {
                java.util.Map<String, Object> receiptData = java.util.Map.of(
                        "messageId", message.getId(),
                        "conversationId", message.getConversationId(),
                        "deliveryStatus", "delivered"
                );
                pushToUser(message.getSenderId(), "deliveryReceipt", receiptData);
            }
        }

        log.debug("消息推送完成: conversationId={}, 成员数={}, 接收者={}", message.getConversationId(), members.size(), recipientCount);
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
            MessageVO payload = withPerspective(message, userId);
            pushFrameToUser(userId, toJson(buildFrame("recall", payload)), true);
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
            pushFrameToUser(userId, json, true);
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
            MessageVO payload = withPerspective(message, userId);
            pushFrameToUser(userId, toJson(buildFrame("edit", payload)), true);
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
        // 校验提交回执的用户必须是该消息所属会话的成员，
        // 防止任意用户对任意消息伪造 deliveryReceipt。
        long memberCount = memberMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(com.linkx.server.entity.ImConversationMember::getConversationId).eq(msg.getConversationId())
                        .and(com.linkx.server.entity.ImConversationMember::getUserId).eq(userId)
        );
        if (memberCount == 0L) {
            sendErrorToSender(userId, new CustomException(403, "仅会话成员可提交送达回执"));
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
                        .and(ImConversationMember::getDeleted).eq(0)
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
     * 向指定用户的所有在线端推送自定义 WS 帧（通话信令等），并跨实例广播。
     */
    public void pushToUser(Long userId, String action, Object data) {
        if (userId == null) {
            return;
        }
        pushFrameToUser(userId, toJson(buildFrame(action, data)), true);
    }

    /**
     * 仅本机投递（供 presence / 集群订阅回调使用，避免二次广播）。
     */
    public void pushToUserLocal(Long userId, String action, Object data) {
        if (userId == null) {
            return;
        }
        pushFrameToUser(userId, toJson(buildFrame(action, data)), false);
    }

    /**
     * 本机 Channel 写帧。
     */
    public void deliverLocal(Long userId, String json) {
        if (userId == null || json == null || json.isBlank()) {
            return;
        }
        ChannelGroup channels = channelManager.getChannels(userId);
        if (channels == null || channels.isEmpty()) {
            return;
        }
        for (Channel channel : channels) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }

    /**
     * @param clusterFanout true 时本机投递 + Redis 广播，他机再投递本机连接
     */
    public void pushFrameToUser(Long userId, String json, boolean clusterFanout) {
        if (userId == null || json == null || json.isBlank()) {
            return;
        }
        deliverLocal(userId, json);
        if (clusterFanout) {
            publishClusterPush(userId, json);
        }
    }

    private void publishClusterPush(Long userId, String json) {
        try {
            Map<String, String> payload = new HashMap<>(4);
            payload.put("userId", String.valueOf(userId));
            payload.put("frame", json);
            payload.put("origin", presenceService.getInstanceId());
            redisTemplate.opsForStream().add(
                    org.springframework.data.redis.connection.stream.MapRecord.create(
                            CLUSTER_PUSH_STREAM, payload));
            // 近似裁剪，限制 Stream 长度，避免无限增长
            redisTemplate.opsForStream().trim(CLUSTER_PUSH_STREAM, CLUSTER_PUSH_STREAM_MAXLEN, true);
        } catch (Exception e) {
            log.warn("发布跨实例 IM 推送失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    /**
     * 向会话全体在线成员推送自定义事件（如会议进行中顶栏同步）。
     */
    public void pushActionToConversationMembers(Long conversationId, String action, Object data) {
        if (conversationId == null || action == null || action.isBlank()) {
            return;
        }
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
        );
        for (ImConversationMember member : members) {
            pushToUser(member.getUserId(), action, data);
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
        // DB 查询与批量序列化放到 imPushExecutor，避免阻塞 Netty event-loop
        try {
            ((ExecutorService) imPushExecutor).submit(() -> {
                try {
                    doHandleSync(userId, frame, channel);
                } catch (Exception e) {
                    log.warn("handleSync 失败: userId={}, err={}", userId, e.toString());
                    try {
                        ImWsFrame err = new ImWsFrame();
                        err.setAction("syncDone");
                        err.setCode(500);
                        err.setMessage("同步失败");
                        if (channel.isActive()) {
                            channel.writeAndFlush(new TextWebSocketFrame(toJson(err)));
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("IM 推送线程池饱和，拒绝 sync userId={}", userId);
            ImWsFrame err = new ImWsFrame();
            err.setAction("syncDone");
            err.setCode(503);
            err.setMessage("服务繁忙，请稍后重试");
            channel.writeAndFlush(new TextWebSocketFrame(toJson(err)));
        }
    }

    private void doHandleSync(Long userId, ImWsFrame frame, Channel channel) {
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
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(toJson(resp)));
            }
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
        // 单批拉取上限可配置（linkx.im.sync-batch-size），多取 1 条用于判断是否还有更多
        int batchSize = Math.max(1, linkxProperties.getIm().getSyncBatchSize());
        // 游标与排序统一用 id（雪花），避免 createTime 排序 + id 游标导致漏/重
        qw.orderBy(ImMessage::getId, true).limit(batchSize + 1);

        List<ImMessage> offlineMessages = messageMapper.selectListByQuery(qw);

        // 通过多取的 1 条判断 hasMore，客户端可基于本批最后一条 id 继续发起 sync 拉取剩余
        boolean hasMore = offlineMessages.size() > batchSize;
        if (hasMore) {
            offlineMessages = offlineMessages.subList(0, batchSize);
        }

        // 转换为 MessageVO 并推送
        if (!offlineMessages.isEmpty()) {
            Set<Long> senderIds = offlineMessages.stream().map(ImMessage::getSenderId).collect(java.util.stream.Collectors.toSet());
            Map<Long, com.linkx.server.entity.SysUser> senderMap = sysUserMapper.selectListByQuery(
                    com.mybatisflex.core.query.QueryWrapper.create()
                            .where(com.linkx.server.entity.SysUser::getId).in(senderIds)
            ).stream().collect(java.util.stream.Collectors.toMap(
                    com.linkx.server.entity.SysUser::getId, u -> u, (a, b) -> a));

            for (ImMessage msg : offlineMessages) {
                if (!channel.isActive()) {
                    return;
                }
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
        Long nextCursor = hasMore && !offlineMessages.isEmpty()
                ? offlineMessages.get(offlineMessages.size() - 1).getId()
                : null;
        java.util.Map<String, Object> respData = new java.util.HashMap<>();
        respData.put("userId", userId);
        respData.put("offlineCount", offlineMessages.size());
        respData.put("hasMore", hasMore);
        if (nextCursor != null) {
            respData.put("nextCursor", nextCursor);
        }
        resp.setData(respData);
        if (channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(toJson(resp)));
        }
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

    // ==================== 消息风暴检测（委托 MessageStormService） ====================

    /**
     * @deprecated 使用 {@link MessageStormService#checkAndRecordUserStorm(Long)}
     */
    public boolean detectMessageStorm(Long userId) {
        return messageStormService.checkAndRecordUserStorm(userId);
    }

    /**
     * 获取用户风暴 Redis 时间戳列表（兼容旧接口；正式报表请查 im_message_storm_event）。
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
