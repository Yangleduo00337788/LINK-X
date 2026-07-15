package com.linkx.server.im;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 空闲超时处理：超时关闭连接
 */
@Slf4j
@ChannelHandler.Sharable
public class ImWebSocketIdleHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.debug("WebSocket 连接空闲超时，关闭连接");
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}