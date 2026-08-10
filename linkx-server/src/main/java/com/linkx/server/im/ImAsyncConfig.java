package com.linkx.server.im;


/**
 * 作者：yangleduo
 */
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

    /**
     * 单聊/小群发送与 sync/storm 等业务 IO 线程池。
     */
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
     * 大群扇出专用线程池，与 imPushExecutor 隔离，避免 500+ 成员群发占满队列导致单聊 503。
     */
    @Bean(name = "imFanoutExecutor")
    public ExecutorService imFanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("im-fanout-");
        // 扇出可降级：队列满时丢弃最旧批次任务，避免拖垮发送路径
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
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

    /**
     * MinIO 文件清理专用线程池（非关键路径，可丢弃）。
     * <p>
     * 有界队列 + DiscardPolicy：队列满时丢弃清理任务，依赖下次清理或合规兜底，
     * 避免清理任务阻塞主业务流程。
     * </p>
     */
    @Bean(name = "minioCleanupExecutor")
    public ExecutorService minioCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("minio-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 管理端异步导出线程池。
     * <p>
     * 有界队列 + CallerRunsPolicy：队列满时回落到调用线程，避免导出任务静默丢弃。
     * </p>
     */
    @Bean(name = "adminExportExecutor")
    public java.util.concurrent.Executor adminExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("admin-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
