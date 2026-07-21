package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.SendMessageDTO;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.service.ChatService;
import com.mybatisflex.core.query.QueryWrapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final ImChannelManager channelManager;
    private final ObjectMapper objectMapper;
    private final Executor imPushExecutor;

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
     */
    public void pushToConversationMembers(MessageVO message, Long senderId, String clientMsgId) {
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
            ImWsFrame frame;
            // 仅客户端上行（带 clientMsgId）才回 ack；系统提示等服务端写入应对全员推 message
            if (userId.equals(senderId) && clientMsgId != null && !clientMsgId.isBlank()) {
                frame = buildFrame("ack", payload);
                frame.setClientMsgId(clientMsgId);
            } else {
                frame = buildFrame("message", payload);
            }

            String json = toJson(frame);
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }
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
}
