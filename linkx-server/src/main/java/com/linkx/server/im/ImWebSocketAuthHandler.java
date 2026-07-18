package com.linkx.server.im;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.TokenService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImWebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private static final String ACCESS_TOKEN_PROTOCOL = "linkx-access-token";

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final LinkxProperties linkxProperties;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest request) {
            // Origin 校验（CSWSH 防护）
            if (!isOriginAllowed(request)) {
                log.warn("WebSocket 拒绝非法 Origin: {} (白名单={})",
                        request.headers().get("Origin"),
                        linkxProperties.getCors().getAllowedOrigins());
                reject(ctx, msg);
                return;
            }

            // 浏览器：query 传 token；Electron：子协议传 token。优先 query（浏览器更稳）
            String token = extractTokenFromQuery(request.uri());
            if (token == null || token.isBlank()) {
                token = extractTokenFromProtocol(request);
            }
            if (token == null || token.isBlank()) {
                log.warn("WebSocket 鉴权失败: 缺少 token, uri={}, origin={}",
                        request.uri(), request.headers().get("Origin"));
                reject(ctx, msg);
                return;
            }

            // Electron 客户端声明了子协议时，服务端必须回写匹配项，否则 Chromium 会直接关连接（1006）。
            // 握手前只保留命名协议，去掉 JWT，避免 Netty 把 JWT 当第二个 subprotocol。
            if (request.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL)) {
                request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, ACCESS_TOKEN_PROTOCOL);
            }

            try {
                if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                    reject(ctx, msg);
                    return;
                }
                tokenService.assertAccessTokenActive(token);
                Long userId = jwtUtils.getUserIdFromToken(token);
                // 去掉 query，交给 WebSocketServerProtocolHandler 做路径匹配
                String path = request.uri().split("\\?")[0];
                request.setUri(path);
                ctx.channel().attr(ImChannelAttributes.USER_ID).set(userId);
                channelManager.add(userId, ctx.channel());
                log.debug("WebSocket 鉴权成功: userId={}, origin={}",
                        userId, request.headers().get("Origin"));
                ctx.fireChannelRead(msg);
            } catch (Exception e) {
                log.warn("WebSocket 鉴权失败: {}", e.getMessage());
                reject(ctx, msg);
            }
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channelManager.remove(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("WebSocket 鉴权通道异常: {}", cause.getMessage());
        ctx.close();
    }

    private boolean isOriginAllowed(FullHttpRequest request) {
        String origin = request.headers().get("Origin");
        // 无 Origin（Electron / 部分桌面壳）直接放行
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (origin.startsWith("file://") || origin.startsWith("app://")) {
            return true;
        }

        List<String> allowed = linkxProperties.getCors().getAllowedOrigins();
        if (allowed != null) {
            for (String item : allowed) {
                if (origin.equals(item)) {
                    return true;
                }
            }
        }

        // 本地 Vite 开发：浏览器必带 http://localhost:端口 Origin；白名单未命中时仍放行本机
        if (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:")) {
            return true;
        }

        return false;
    }

    private String extractTokenFromQuery(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> tokens = decoder.parameters().get("token");
        return tokens != null && !tokens.isEmpty() ? tokens.get(0) : null;
    }

    /**
     * 从 Sec-WebSocket-Protocol 子协议提取 token。
     * 客户端发送：{@code Sec-WebSocket-Protocol: linkx-access-token, <jwt>}
     */
    private String extractTokenFromProtocol(FullHttpRequest request) {
        String header = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.split(",");
        for (int i = 0; i < parts.length - 1; i++) {
            if (ACCESS_TOKEN_PROTOCOL.equalsIgnoreCase(parts[i].trim())) {
                return parts[i + 1].trim();
            }
        }
        if (parts.length == 1) {
            String trimmed = parts[0].trim();
            if (trimmed.startsWith("eyJ")) {
                return trimmed;
            }
        }
        return null;
    }

    private void reject(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            request.release();
        }
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(future -> ctx.close());
    }
}
