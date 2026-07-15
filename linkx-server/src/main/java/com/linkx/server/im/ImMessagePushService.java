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

@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessagePushService {

    private final ChatService chatService;
    private final ImConversationMemberMapper memberMapper;
    private final ImChannelManager channelManager;
    private final ObjectMapper objectMapper;

    public MessageVO handleSend(Long senderId, ImWsFrame frame) {
        SendMessageDTO dto = new SendMessageDTO();
        dto.setConversationId(parseId(frame.getConversationId(), "会话 ID"));
        dto.setMsgType(frame.getMsgType());
        dto.setContent(frame.getContent());
        dto.setFileName(frame.getFileName());
        dto.setFileSize(frame.getFileSize());
        dto.setFileUrl(frame.getFileUrl());
        dto.setClientMsgId(frame.getClientMsgId());

        MessageVO message = chatService.sendMessage(senderId, dto);
        pushToConversationMembers(message, senderId, frame.getClientMsgId());
        return message;
    }

    public void pushToConversationMembers(MessageVO message, Long senderId, String clientMsgId) {
        List<ImConversationMember> members = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getConversationId).eq(message.getConversationId())
        );

        for (ImConversationMember member : members) {
            Long userId = member.getUserId();
            ChannelGroup channels = channelManager.getChannels(userId);
            if (channels == null || channels.isEmpty()) {
                continue;
            }

            MessageVO payload = withPerspective(message, userId);
            ImWsFrame frame;
            if (userId.equals(senderId)) {
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
                .createTime(message.getCreateTime())
                .isSelf(message.getSenderId().equals(viewerId))
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
