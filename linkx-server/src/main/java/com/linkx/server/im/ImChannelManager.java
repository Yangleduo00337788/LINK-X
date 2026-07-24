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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void add(Long userId, Channel channel) {
        userChannels.computeIfAbsent(userId, id -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE))
                .add(channel);
        log.debug("用户 {} 上线，当前连接数 {}", userId, userChannels.get(userId).size());
    }

    public void remove(Channel channel) {
        userChannels.entrySet().removeIf(entry -> {
            entry.getValue().remove(channel);
            return entry.getValue().isEmpty();
        });
    }

    public ChannelGroup getChannels(Long userId) {
        return userChannels.get(userId);
    }

    public boolean isOnline(Long userId) {
        ChannelGroup group = userChannels.get(userId);
        return group != null && !group.isEmpty();
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
