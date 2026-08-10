package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
/**
 * 管理端异步任务的操作者上下文（无 HTTP Request 时供 DataScope 等切面使用）。
 */
public final class AdminActorContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AdminActorContext() {
    }

    public static void setUserId(Long userId) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }

    public static void runAs(Long userId, Runnable action) {
        Long previous = USER_ID.get();
        setUserId(userId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                setUserId(previous);
            }
        }
    }
}
