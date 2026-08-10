package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。
 * <p>
 * 标注在 Controller 方法或类上，由 {@code RoleRequiredAspect} 拦截，
 * 校验当前登录用户是否拥有指定角色之一（或全部，由 logicalOr 控制）。
 * 不满足抛出 403 CustomException。
 * </p>
 * <p>
 * 用法示例：
 * {@code @RequireRole(RbacConstants.ROLE_ADMIN)}
 * {@code @RequireRole(adminPortal = true)} — 允许全部可登录管理端的角色
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 需要校验的角色编码集合；{@link #adminPortal()} 为 true 时忽略 */
    String[] value() default {};

    /**
     * 为 true 时使用 {@code AdminConstants.ADMIN_ROLES}（可登录管理端的全部角色），
     * 避免各 Controller 重复罗列角色码。
     */
    boolean adminPortal() default false;

    /**
     * 逻辑关系：true=满足其一即可（默认），false=必须全部满足
     */
    boolean logicalOr() default true;
}
