package com.linkx.server.common;

/**
 * 数据权限上下文。
 * <p>
 * 通过 ThreadLocal 在当前线程内传递数据范围标识。
 * {@link com.linkx.server.config.aspect.DataScopeAspect} 在方法执行前写入，
 * Service 方法读取后拼接过滤条件，方法执行完毕由切面清除，避免线程池复用导致的脏读。
 * </p>
 * <p>
 * 语义：userId 为 null 表示不限制（admin 全量可见）；非 null 表示仅可见该用户数据。
 * </p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /** 设置当前数据范围的用户 ID（null 表示不限制） */
    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /** 获取当前数据范围的用户 ID（null 表示不限制） */
    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    /** 清除当前线程的数据范围标识，必须在使用后调用以防内存泄漏 */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
