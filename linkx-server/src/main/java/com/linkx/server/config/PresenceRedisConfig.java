package com.linkx.server.config;

import com.linkx.server.im.PresenceEventSubscriber;
import com.linkx.server.service.impl.PresenceServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class PresenceRedisConfig {

    @Bean
    RedisMessageListenerContainer presenceMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PresenceEventSubscriber presenceEventSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // IM 跨实例推送已迁至 Redis Stream（ImClusterPushSubscriber），此处仅订阅 presence 事件
        container.addMessageListener(
                presenceEventSubscriber,
                new ChannelTopic(PresenceServiceImpl.EVENTS_CHANNEL));
        return container;
    }
}
