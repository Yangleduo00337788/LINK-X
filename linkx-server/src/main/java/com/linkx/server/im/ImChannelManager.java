package com.linkx.server.im;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ImChannelManager {

    private final Map<Long, ChannelGroup> userChannels = new ConcurrentHashMap<>();

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
}
