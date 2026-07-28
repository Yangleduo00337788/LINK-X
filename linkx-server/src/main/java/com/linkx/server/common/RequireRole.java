package com.linkx.server.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。
 * <p>
 * 标注在 Controller 方法上，由 {@code RoleRequiredAspect} 拦截，
 * 校验当前登录用户是否拥有指定角色之一（或全部，由 logicalOr 控制）。
 * 不满足抛出 403 CustomException。
 * </p>
 * <p>
 * 用法示例：{@code @RequireRole(RbacConstants.ROLE_ADMIN)}
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 需要校验的角色编码集合 */
    String[] value();

    /**
     * 逻辑关系：true=满足其一即可（默认），false=必须全部满足
     */
    boolean logicalOr() default true;
}
