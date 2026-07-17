package com.linkx.server.config.aspect;

import java.lang.annotation.*;

/**
 * 审计操作注解
 * 标注在 Controller 方法上，自动记录审计日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditAction {

    /**
     * 操作类型
     */
    String operationType();

    /**
     * 操作描述（支持 SpEL 表达式）
     */
    String description();

    /**
     * 是否记录请求参数
     */
    boolean logParams() default false;

    /**
     * 是否记录响应结果
     */
    boolean logResult() default false;
}
