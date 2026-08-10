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
 * 数据权限注解。
 * <p>
 * 标注在 Service 方法上，由 {@code DataScopeAspect} 拦截，
 * 按角色 {@code data_scope}（全部 / 仅本人 / 本部门及下级）写入 {@link DataScopeContext}。
 * Service 通过 {@link DataScopeContext#getAllowedUserIds()} 拼接过滤条件。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 用户表别名，预留扩展 */
    String userAlias() default "u";
}
