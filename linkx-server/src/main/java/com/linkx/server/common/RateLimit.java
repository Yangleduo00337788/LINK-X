package com.linkx.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务接口限流注解
 * <p>
 * 使用方法：在 Controller 方法上标注
 * {@code @RateLimit(scope = "friend:search", value = 30, window = 60)}
 * <p>
 * 当 {@code window=60} 时，{@code value} 仅作兜底；实际阈值由管理端「风控策略」中的
 * 搜索/列表/写入/上传每分钟限额覆盖（按 scope 自动归类）。
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
