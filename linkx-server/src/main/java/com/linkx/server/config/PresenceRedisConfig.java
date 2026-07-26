package com.linkx.server.config;

import com.linkx.server.im.ImClusterPushSubscriber;
import com.linkx.server.im.ImMessagePushService;
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
            PresenceEventSubscriber presenceEventSubscriber,
            ImClusterPushSubscriber imClusterPushSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                presenceEventSubscriber,
                new ChannelTopic(PresenceServiceImpl.EVENTS_CHANNEL));
        container.addMessageListener(
                imClusterPushSubscriber,
                new ChannelTopic(ImMessagePushService.CLUSTER_PUSH_CHANNEL));
        return container;
    }
}
