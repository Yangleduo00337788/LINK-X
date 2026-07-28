package com.linkx.server.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解。
 * <p>
 * 标注在 Service 方法上，由 {@code DataScopeAspect} 拦截，
 * 根据当前登录用户的角色判定数据可见范围：
 * <ul>
 *   <li>admin 角色：可见全部数据（{@link DataScopeContext#getUserId()} 返回 null）</li>
 *   <li>普通用户：仅可见本人数据（{@link DataScopeContext#getUserId()} 返回当前 userId）</li>
 * </ul>
 * Service 方法内通过 {@link DataScopeContext#getUserId()} 读取范围标识，
 * 手动拼接到 MyBatis-Flex QueryWrapper 的过滤条件中。
 * </p>
 * <p>
 * 当前为框架实现，暂未强制应用到现有 Service，避免破坏既有功能。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 用户表别名，用于拼接 SQL 条件（如 "u" 对应 u.id = ?），预留扩展 */
    String userAlias() default "u";
}
