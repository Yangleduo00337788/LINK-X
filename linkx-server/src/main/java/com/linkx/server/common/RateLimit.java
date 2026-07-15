package com.linkx.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务接口限流注解
 * <p>
 * 使用方法：在 Controller 方法上标注
 * {@code @RateLimit(scope = "search", value = 30, window = 60)}
 * 表示该接口每分钟最多 30 次（按用户 + IP 维度计数）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度名（用于 Redis key 隔离），如 "search" / "upload" / "write" */
    String scope() default "default";

    /** 最大次数 */
    int value() default 60;

    /** 时间窗口（秒） */
    int window() default 60;

    /** 是否按用户维度（false 则按 IP） */
    boolean byUser() default true;
}
