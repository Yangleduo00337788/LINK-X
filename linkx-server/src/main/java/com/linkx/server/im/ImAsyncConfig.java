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

    /**
     * 审计日志专用线程池。
     * <p>
     * 有界队列 + CallerRunsPolicy：队列满时由调用线程执行，避免审计日志丢失
     * （审计日志不可丢，宁可阻塞主流程也不能丢弃）。
     * </p>
     */
    @Bean(name = "auditExecutor")
    public java.util.concurrent.Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
