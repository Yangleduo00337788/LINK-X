package com.linkx.server.im;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.PresenceService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ImWebSocketMessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ImMessagePushService pushService;
    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            log.debug("WebSocket 握手完成: {}", ctx.channel().remoteAddress());
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        Long userId = ctx.channel().attr(ImChannelAttributes.USER_ID).get();
        if (userId == null) {
            pushService.sendError(ctx.channel(), 401, "未认证");
            ctx.close();
            return;
        }

        try {
            ImWsFrame wsFrame = objectMapper.readValue(frame.text(), ImWsFrame.class);
            if (wsFrame.getAction() == null) {
                pushService.sendError(ctx.channel(), 400, "缺少 action 字段");
                return;
            }

            switch (wsFrame.getAction()) {
                case "ping" -> {
                    presenceService.touch(userId);
                    ctx.writeAndFlush(new TextWebSocketFrame(pushService.buildPong()));
                }
                case "send" -> pushService.handleSend(userId, wsFrame);
                case "retry" -> pushService.handleRetry(userId, wsFrame);
                case "deliveryReceipt" -> pushService.handleDeliveryReceipt(userId, wsFrame);
                case "sync" -> pushService.handleSync(userId, wsFrame, ctx.channel());
                case "recall" -> pushService.handleRecall(userId, wsFrame);
                case "edit" -> pushService.handleEdit(userId, wsFrame);
                case "typing" -> pushService.handleTyping(userId, wsFrame);
                default -> pushService.sendError(ctx.channel(), 400, "不支持的 action: " + wsFrame.getAction());
            }
        } catch (CustomException e) {
            pushService.sendError(ctx.channel(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
            pushService.sendError(ctx.channel(), 500, "消息处理失败");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("WebSocket 消息通道异常: {}", cause.getMessage());
        ctx.close();
    }
}
