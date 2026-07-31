package com.linkx.server.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ImChannelManager {

    public static final String DEFAULT_DEVICE_ID = "default-web-device";

    private final Map<Long, ChannelGroup> userChannels = new ConcurrentHashMap<>();
    /**
     * 反向索引：Channel -> userId，避免 remove(Channel) 时全表扫描 userChannels。
     * 维护成本：add/remove 各一次 O(1) put/remove，与 ChannelGroup 内部维护一致。
     */
    private final Map<Channel, Long> channelToUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return true 表示本机该用户从无连接变为有连接（本机首连）
     */
    public boolean add(Long userId, Channel channel) {
        ChannelGroup group = userChannels.computeIfAbsent(
                userId, id -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        boolean becameOnline = group.isEmpty();
        group.add(channel);
        channelToUser.put(channel, userId);
        log.debug("用户 {} 上线，当前连接数 {}", userId, group.size());
        return becameOnline;
    }

    /**
     * @return true 表示本机该用户已无剩余连接（本机末断）
     */
    public boolean remove(Channel channel) {
        Long userId = channelToUser.remove(channel);
        if (userId == null) {
            // 未在本机注册（可能已被并发 remove），无需扫描
            return false;
        }
        ChannelGroup group = userChannels.get(userId);
        if (group == null) {
            return false;
        }
        boolean removed = group.remove(channel);
        if (removed && group.isEmpty()) {
            // 用 remove(key) 替代 removeIf，避免对整个 userChannels 做扫描；
            // computeIfPresent 防止并发 add 引入的新 group 被误删
            userChannels.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
            return true;
        }
        return false;
    }

    public ChannelGroup getChannels(Long userId) {
        return userChannels.get(userId);
    }

    public boolean isOnline(Long userId) {
        ChannelGroup group = userChannels.get(userId);
        return group != null && !group.isEmpty();
    }

    /** 本机当前在线用户 ID 快照 */
    public java.util.Set<Long> localOnlineUserIds() {
        return java.util.Set.copyOf(userChannels.keySet());
    }

    /** 向本机全部在线连接投递同一帧 */
    public void deliverToAllLocal(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        for (ChannelGroup group : userChannels.values()) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            for (Channel channel : group) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            }
        }
    }

    /**
     * 强制断开指定设备的 WebSocket：先推送 force_logout，再关闭通道。
     *
     * @return 关闭的连接数
     */
    public int disconnectDevice(Long userId, String deviceId) {
        ChannelGroup group = userChannels.get(userId);
        if (group == null || group.isEmpty()) {
            return 0;
        }
        String target = (deviceId == null || deviceId.isBlank()) ? DEFAULT_DEVICE_ID : deviceId.trim();
        List<Channel> matched = new ArrayList<>();
        for (Channel channel : group) {
            String channelDeviceId = channel.attr(ImChannelAttributes.DEVICE_ID).get();
            if (channelDeviceId == null || channelDeviceId.isBlank()) {
                channelDeviceId = DEFAULT_DEVICE_ID;
            }
            if (target.equals(channelDeviceId)) {
                matched.add(channel);
            }
        }
        String payload = buildForceLogoutPayload();
        for (Channel channel : matched) {
            try {
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            } catch (Exception e) {
                log.debug("推送 force_logout 失败: {}", e.getMessage());
            }
            channel.close();
        }
        return matched.size();
    }

    private String buildForceLogoutPayload() {
        try {
            ImWsFrame frame = new ImWsFrame();
            frame.setAction("force_logout");
            frame.setCode(401);
            frame.setMessage("设备已被强制下线");
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            return "{\"action\":\"force_logout\",\"code\":401,\"message\":\"设备已被强制下线\"}";
        }
    }
}
