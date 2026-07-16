package com.linkx.server.im;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.TokenService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImWebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final LinkxProperties linkxProperties;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest request) {
            // Origin 校验（CSWSH 防护）：未配置白名单时仅允许桌面客户端与同源
            if (!isOriginAllowed(request)) {
                log.warn("WebSocket 拒绝非法 Origin: {}", request.headers().get("Origin"));
                reject(ctx, msg);
                return;
            }

            // 优先从 Sec-WebSocket-Protocol 子协议读取 token，避免 token 出现在 URL Query/日志中
            String token = extractTokenFromProtocol(request);
            if (token == null || token.isBlank()) {
                // 回退：从 URL Query 读取（兼容旧客户端）
                token = extractTokenFromQuery(request.uri());
            }
            if (token == null || token.isBlank()) {
                reject(ctx, msg);
                return;
            }
            try {
                if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                    reject(ctx, msg);
                    return;
                }
                tokenService.assertAccessTokenActive(token);
                Long userId = jwtUtils.getUserIdFromToken(token);
                String path = request.uri().split("\\?")[0];
                request.setUri(path);
                ctx.channel().attr(ImChannelAttributes.USER_ID).set(userId);
                channelManager.add(userId, ctx.channel());
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
        // 无 Origin 头的请求（如同源请求/原生客户端）直接放行
        if (origin == null || origin.isBlank()) {
            return true;
        }
        List<String> allowed = linkxProperties.getCors().getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            // 未配置白名单时，仅允许 Electron 桌面应用。
            // 拒绝 http://localhost 等浏览器来源，防止 CSWSH 攻击
            // （恶意页面诱导浏览器建立 WebSocket 后即可读取用户消息）。
            return origin.startsWith("file://") || origin.startsWith("app://");
        }
        return allowed.contains(origin);
    }

    private String extractTokenFromQuery(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> tokens = decoder.parameters().get("token");
        return tokens != null && !tokens.isEmpty() ? tokens.get(0) : null;
    }

    /**
     * 从 Sec-WebSocket-Protocol 子协议提取 token。
     * 客户端发送：`Sec-WebSocket-Protocol: linkx-access-token, <jwt>`
     * 服务器回写相同子协议头完成协议协商。
     */
    private String extractTokenFromProtocol(FullHttpRequest request) {
        String header = request.headers().get("Sec-WebSocket-Protocol");
        if (header == null || header.isBlank()) {
            return null;
        }
        // 解析逗号分隔的子协议名，取出形如 "linkx-access-token, eyJ..." 中的 token
        String[] parts = header.split(",");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("linkx-access-token".equalsIgnoreCase(parts[i].trim())) {
                return parts[i + 1].trim();
            }
        }
        // 容错：直接就是 token
        if (parts.length == 1) {
            String trimmed = parts[0].trim();
            if (trimmed.startsWith("eyJ")) {  // JWT 开头
                return trimmed;
            }
        }
        return null;
    }

    private void reject(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            request.release();
        }
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
        ctx.writeAndFlush(response).addListener(future -> ctx.close());
    }
}
