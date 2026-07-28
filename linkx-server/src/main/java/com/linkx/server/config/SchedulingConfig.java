package com.linkx.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器配置。
 * <p>
 * Spring 默认使用单线程的 {@link org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor}
 * 执行所有 @Scheduled 任务，导致：
 * <ul>
 *   <li>多个任务串行执行，相互阻塞；</li>
 *   <li>单个任务抛异常或阻塞会拖延其他任务（如红包过期退款被消息留存清理卡住）。</li>
 * </ul>
 * 此处显式声明 {@link ThreadPoolTaskScheduler}，提供多线程调度与异常隔离。
 * </p>
 */
@Slf4j
@Configuration
public class SchedulingConfig {

    /**
     * 定时任务调度线程池。
     * <p>
     * 池大小取 max(4, CPU 核数)，覆盖当前 5 个 @Scheduled 任务（红包过期、消息留存、群禁言、心跳、敏感词刷新）。
     * 任务内异常由 errorHandler 记录日志，避免静默吞掉导致任务停止调度。
     * </p>
     */
    @Bean
    public TaskScheduler taskScheduler() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("linkx-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(t ->
                log.error("[ScheduledTask] 执行异常", t));
        return scheduler;
    }
}
