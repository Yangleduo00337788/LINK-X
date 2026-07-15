package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.JwtUtils;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.TokenService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketServer implements ApplicationRunner {

    private final LinkxProperties linkxProperties;
    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final ImMessagePushService pushService;
    private final ObjectMapper objectMapper;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int port = linkxProperties.getIm().getWebsocketPort();
        if (port <= 0) {
            log.info("WebSocket 端口未启用，跳过 IM 服务启动");
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ImWebSocketChannelInitializer(
                        linkxProperties, jwtUtils, tokenService, channelManager, pushService, objectMapper));

        ChannelFuture future = bootstrap.bind(port);
        future.addListener(bindFuture -> {
            if (bindFuture.isSuccess()) {
                serverChannel = ((ChannelFuture) bindFuture).channel();
                log.info("LinkX IM WebSocket 服务已启动，端口: {}, 路径: {}",
                        port, linkxProperties.getIm().getWebsocketPath());
            } else {
                log.error("LinkX IM WebSocket 服务启动失败，端口: {}", port, bindFuture.cause());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            log.info("LinkX IM WebSocket 服务已关闭");
        }
    }
}
