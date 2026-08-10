package com.linkx.server.im;


/**
 * 作者：yangleduo
 */
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 空闲超时：仅在读空闲时关闭连接（对端长时间无心跳/上行）。
 * 写空闲不关闭——安静会话无服务端下行属正常，避免误断连。
 */
@Slf4j
@ChannelHandler.Sharable
public class ImWebSocketIdleHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            if (idle.state() == IdleState.READER_IDLE) {
                log.debug("WebSocket 读空闲超时，关闭连接");
                ctx.close();
                return;
            }
            // WRITER_IDLE / ALL_IDLE：忽略
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
