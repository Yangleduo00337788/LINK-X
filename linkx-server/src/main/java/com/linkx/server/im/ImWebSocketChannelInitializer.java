package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.TokenService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImWebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final LinkxProperties linkxProperties;
    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final ImMessagePushService pushService;
    private final ObjectMapper objectMapper;

    @Override
    protected void initChannel(SocketChannel ch) {
        String wsPath = linkxProperties.getIm().getWebsocketPath();
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(65536));
        // 空闲连接超时（读60s/写30s）防止连接泄漏
        pipeline.addLast(new io.netty.handler.timeout.IdleStateHandler(60, 30, 0));
        pipeline.addLast(new ImWebSocketIdleHandler());
        pipeline.addLast(new ImWebSocketAuthHandler(jwtUtils, tokenService, channelManager, linkxProperties));
        pipeline.addLast(new WebSocketServerProtocolHandler(wsPath, null, true));
        pipeline.addLast(new ImWebSocketMessageHandler(pushService, objectMapper));
    }
}
