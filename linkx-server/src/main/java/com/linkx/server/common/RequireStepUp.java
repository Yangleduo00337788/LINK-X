package com.linkx.server.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 高危操作二次验证（step-up）。
 * <p>
 * 标注在 Controller 方法上，由 {@code StepUpRequiredAspect} 校验请求头
 * {@code X-Step-Up-Token} 是否覆盖声明的 action。
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireStepUp {

    /**
     * 动作标识，通常与权限码一致（如 {@code admin:user:ban}）。
     * 用于绑定 step-up token 的 scope。
     */
    String value();
}
