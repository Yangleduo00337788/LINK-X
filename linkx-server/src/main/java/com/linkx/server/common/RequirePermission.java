package com.linkx.server.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 * <p>
 * 标注在 Controller 方法上，由 {@code PermissionRequiredAspect} 拦截，
 * 校验当前登录用户是否拥有指定权限之一（或全部，由 logicalOr 控制）。
 * 持有通配符权限 {@link RbacConstants#PERM_ALL} 视为通过全部权限校验。
 * 不满足抛出 403 CustomException。
 * </p>
 * <p>
 * 用法示例：{@code @RequirePermission("user:delete")}
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 需要校验的权限编码集合 */
    String[] value();

    /**
     * 逻辑关系：true=满足其一即可（默认），false=必须全部满足
     */
    boolean logicalOr() default true;
}
