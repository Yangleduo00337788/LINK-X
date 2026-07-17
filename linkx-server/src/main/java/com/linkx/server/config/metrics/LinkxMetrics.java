package com.linkx.server.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 自定义业务指标
 * 用于记录 LinkX 业务相关的指标数据
 */
@Component
public class LinkxMetrics {

    private final MeterRegistry meterRegistry;

    // 登录指标
    @Getter
    private final Counter loginSuccessCounter;
    @Getter
    private final Counter loginFailureCounter;
    @Getter
    private final Timer loginDurationTimer;

    // 注册指标
    @Getter
    private final Counter registerSuccessCounter;
    @Getter
    private final Counter registerFailureCounter;

    // Token 指标
    @Getter
    private final Counter tokenRefreshSuccessCounter;
    @Getter
    private final Counter tokenRefreshFailureCounter;

    // 消息指标
    @Getter
    private final Counter messageSentCounter;
    @Getter
    private final Timer messageSendDurationTimer;

    // 文件上传指标
    @Getter
    private final Counter fileUploadSuccessCounter;
    @Getter
    private final Counter fileUploadFailureCounter;

    // Redis 操作指标
    @Getter
    private final Timer redisOperationTimer;

    // 数据库操作指标
    @Getter
    private final Timer databaseOperationTimer;

    // 会话指标（内存缓存）
    private final ConcurrentHashMap<String, Timer> sessionTimers = new ConcurrentHashMap<>();

    public LinkxMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 登录指标
        this.loginSuccessCounter = Counter.builder("linkx.auth.login.success")
                .description("登录成功次数")
                .tag("type", "success")
                .register(meterRegistry);

        this.loginFailureCounter = Counter.builder("linkx.auth.login.failure")
                .description("登录失败次数")
                .tag("type", "failure")
                .register(meterRegistry);

        this.loginDurationTimer = Timer.builder("linkx.auth.login.duration")
                .description("登录耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 注册指标
        this.registerSuccessCounter = Counter.builder("linkx.auth.register.success")
                .description("注册成功次数")
                .register(meterRegistry);

        this.registerFailureCounter = Counter.builder("linkx.auth.register.failure")
                .description("注册失败次数")
                .register(meterRegistry);

        // Token 刷新指标
        this.tokenRefreshSuccessCounter = Counter.builder("linkx.token.refresh.success")
                .description("Token 刷新成功次数")
                .register(meterRegistry);

        this.tokenRefreshFailureCounter = Counter.builder("linkx.token.refresh.failure")
                .description("Token 刷新失败次数")
                .register(meterRegistry);

        // 消息指标
        this.messageSentCounter = Counter.builder("linkx.message.sent")
                .description("发送消息次数")
                .tag("type", "sent")
                .register(meterRegistry);

        this.messageSendDurationTimer = Timer.builder("linkx.message.send.duration")
                .description("消息发送耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 文件上传指标
        this.fileUploadSuccessCounter = Counter.builder("linkx.file.upload.success")
                .description("文件上传成功次数")
                .register(meterRegistry);

        this.fileUploadFailureCounter = Counter.builder("linkx.file.upload.failure")
                .description("文件上传失败次数")
                .register(meterRegistry);

        // Redis 操作指标
        this.redisOperationTimer = Timer.builder("linkx.redis.operation")
                .description("Redis 操作耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 数据库操作指标
        this.databaseOperationTimer = Timer.builder("linkx.database.operation")
                .description("数据库操作耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ============ 便捷方法 ============

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFailure() {
        loginFailureCounter.increment();
    }

    public void recordLoginDuration(long durationMs) {
        loginDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRegisterSuccess() {
        registerSuccessCounter.increment();
    }

    public void recordRegisterFailure() {
        registerFailureCounter.increment();
    }

    public void recordTokenRefreshSuccess() {
        tokenRefreshSuccessCounter.increment();
    }

    public void recordTokenRefreshFailure() {
        tokenRefreshFailureCounter.increment();
    }

    public void recordMessageSent() {
        messageSentCounter.increment();
    }

    public void recordMessageSendDuration(long durationMs) {
        messageSendDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFileUploadSuccess() {
        fileUploadSuccessCounter.increment();
    }

    public void recordFileUploadFailure() {
        fileUploadFailureCounter.increment();
    }

    public <T> T recordRedisOperation(java.util.concurrent.Callable<T> operation) throws Exception {
        return redisOperationTimer.recordCallable(operation);
    }

    public <T> T recordDatabaseOperation(java.util.concurrent.Callable<T> operation) throws Exception {
        return databaseOperationTimer.recordCallable(operation);
    }

    public void recordRedisOperation(Runnable operation) {
        redisOperationTimer.record(operation);
    }

    public void recordDatabaseOperation(Runnable operation) {
        databaseOperationTimer.record(operation);
    }
}
