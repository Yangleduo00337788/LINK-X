package com.linkx.server.im;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * IM 异步消息推送线程池配置
 * 将 Netty event-loop 中的消息推送派发到独立线程池，避免阻塞 IO 线程
 */
@Slf4j
@Configuration
@EnableAsync
public class ImAsyncConfig {

    @Bean(name = "imPushExecutor")
    public ExecutorService imPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("im-push-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}
