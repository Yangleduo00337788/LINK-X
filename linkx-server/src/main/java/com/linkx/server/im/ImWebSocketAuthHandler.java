package com.linkx.server.im;

import com.linkx.server.common.JwtUtils;
import com.linkx.server.common.TokenCookieUtil;
import com.linkx.server.common.TokenType;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.DeviceSessionService;
import com.linkx.server.service.PresenceService;
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
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ImWebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private static final String ACCESS_TOKEN_PROTOCOL = "linkx-access-token";

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final ImChannelManager channelManager;
    private final LinkxProperties linkxProperties;
    private final DeviceSessionService deviceSessionService;
    private final PresenceService presenceService;

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

            // 强制仅通过子协议传 token：避免 JWT 进入 URL query 进而被 access log / 反代日志记录
            String token = extractTokenFromProtocol(request);
            if (token == null || token.isBlank()) {
                // Web 环境兜底：token 在 HttpOnly Cookie 中（浏览器 WebSocket 握手自动携带同站 Cookie），
                // JS 无法读取，故子协议无 token 时从 Cookie 读取。Electron 仍走子协议，不受影响。
                token = TokenCookieUtil.parseCookie(
                        request.headers().get(HttpHeaderNames.COOKIE),
                        TokenCookieUtil.ACCESS_TOKEN_COOKIE);
            }
            if (token == null || token.isBlank()) {
                log.warn("WebSocket 鉴权失败: 缺少子协议 token 与 Cookie, uri={}, origin={}",
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
                // 先解析 query 参数（随后会去掉 query）
                String deviceId = extractParamFromQuery(request.uri(), "deviceId");
                String deviceName = extractParamFromQuery(request.uri(), "deviceName");
                String deviceType = extractParamFromQuery(request.uri(), "deviceType");
                if (deviceId == null || deviceId.isBlank()) {
                    deviceId = ImChannelManager.DEFAULT_DEVICE_ID;
                }

                if (jwtUtils.getTokenType(token) != TokenType.ACCESS) {
                    reject(ctx, msg);
                    return;
                }
                tokenService.assertAccessTokenActive(token, deviceId);
                Long userId = jwtUtils.getUserIdFromToken(token);

                ctx.channel().attr(ImChannelAttributes.DEVICE_ID).set(deviceId);

                // 去掉 query，交给 WebSocketServerProtocolHandler 做路径匹配
                String path = request.uri().split("\\?")[0];
                request.setUri(path);
                ctx.channel().attr(ImChannelAttributes.USER_ID).set(userId);
                channelManager.add(userId, ctx.channel());
                String presenceConnId = UUID.randomUUID().toString().replace("-", "");
                ctx.channel().attr(ImChannelAttributes.PRESENCE_CONN_ID).set(presenceConnId);
                presenceService.markOnline(userId, deviceId, presenceConnId);

                // 注册设备会话（多端同步）
                String ip = ctx.channel().remoteAddress() != null
                        ? ctx.channel().remoteAddress().toString() : "";
                String userAgent = request.headers().get("User-Agent");
                deviceSessionService.registerDevice(userId, deviceId,
                        deviceName, deviceType, ip, userAgent);

                log.debug("WebSocket 鉴权成功: userId={}, deviceId={}, origin={}",
                        userId, deviceId, request.headers().get("Origin"));
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
        Long userId = ctx.channel().attr(ImChannelAttributes.USER_ID).get();
        String deviceId = ctx.channel().attr(ImChannelAttributes.DEVICE_ID).get();
        String presenceConnId = ctx.channel().attr(ImChannelAttributes.PRESENCE_CONN_ID).get();
        channelManager.remove(ctx.channel());
        if (userId != null) {
            presenceService.markOffline(userId, deviceId, presenceConnId);
        }
        // 断连只刷新活跃时间，不删设备行；踢下线由 kickDevice 显式删除
        if (deviceId != null && !deviceId.isBlank()) {
            deviceSessionService.updateLastActive(deviceId);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("WebSocket 鉴权通道异常: {}", cause.getMessage());
        ctx.close();
    }

    private boolean isOriginAllowed(FullHttpRequest request) {
        String origin = request.headers().get("Origin");
        // null/空 Origin 默认拒绝；桌面客户端应配置明确 Origin 白名单（如 linkx:// 或具体 scheme）
        if (origin == null || origin.isBlank()) {
            log.warn("WebSocket 拒绝空 Origin：桌面客户端应配置明确 Origin 白名单");
            return false;
        }

        List<String> allowed = linkxProperties.getCors().getAllowedOrigins();
        if (allowed != null) {
            for (String item : allowed) {
                if (origin.equals(item)) {
                    return true;
                }
            }
        }

        // 仅在明确配置为开发模式时才允许 localhost，生产环境必须显式配置白名单
        boolean isDevMode = Boolean.TRUE.equals(linkxProperties.getApp().getDevModeEnabled());
        if (isDevMode && (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
            log.debug("WebSocket 开发模式允许 localhost origin: {}", origin);
            return true;
        }

        log.debug("WebSocket Origin 不在白名单: {} (白名单={}, devMode={})",
                origin, allowed, isDevMode);
        return false;
    }

    private String extractParamFromQuery(String uri, String param) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> values = decoder.parameters().get(param);
        return values != null && !values.isEmpty() ? values.get(0) : null;
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
