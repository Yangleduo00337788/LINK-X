package com.linkx.server.im;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.PresenceService;
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
    private final DeviceSessionService deviceSessionService;
    private final PresenceService presenceService;

    @Override
    protected void initChannel(SocketChannel ch) {
        String wsPath = linkxProperties.getIm().getWebsocketPath();
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(65536));
        // 仅检测读空闲：安静会话无下行时写空闲属正常，不可因此断连
        pipeline.addLast(new io.netty.handler.timeout.IdleStateHandler(60, 0, 0));
        pipeline.addLast(new ImWebSocketIdleHandler());
        pipeline.addLast(new ImWebSocketAuthHandler(
                jwtUtils, tokenService, channelManager, linkxProperties,
                deviceSessionService, presenceService));
        // 接受命名子协议；浏览器可不带该头（仅 query 鉴权）。checkStartsWith 兼容 /ws?token=...
        pipeline.addLast(new WebSocketServerProtocolHandler(
                wsPath, "linkx-access-token", true, 65536, false, true));
        pipeline.addLast(new ImWebSocketMessageHandler(pushService, objectMapper, presenceService));
    }
}
