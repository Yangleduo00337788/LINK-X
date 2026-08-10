package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import java.util.Collections;
import java.util.Set;

/**
 * 数据权限上下文。
 * <p>
 * {@code allowedUserIds == null} 表示不限制（全部可见）；
 * 非 null 表示仅可见集合内用户的数据。
 * </p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<Set<Long>> ALLOWED_USER_IDS = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /** 不限制（全部可见） */
    public static void setUnrestricted() {
        ALLOWED_USER_IDS.set(null);
    }

    /** 限制为指定用户集合（空集表示无可见数据） */
    public static void setAllowedUserIds(Set<Long> userIds) {
        if (userIds == null) {
            ALLOWED_USER_IDS.set(null);
            return;
        }
        ALLOWED_USER_IDS.set(Collections.unmodifiableSet(userIds));
    }

    /**
     * 可见用户 ID 集合；{@code null} 表示不限制。
     */
    public static Set<Long> getAllowedUserIds() {
        return ALLOWED_USER_IDS.get();
    }

    /**
     * 兼容旧语义：不限制返回 null；仅一人时返回该 ID；多人时返回 null 并由调用方改读 {@link #getAllowedUserIds()}。
     *
     * @deprecated 请使用 {@link #getAllowedUserIds()} / {@link #allows(Long)}
     */
    @Deprecated
    public static Long getUserId() {
        Set<Long> allowed = ALLOWED_USER_IDS.get();
        if (allowed == null) {
            return null;
        }
        if (allowed.size() == 1) {
            return allowed.iterator().next();
        }
        return null;
    }

    /** 当前范围是否允许访问指定用户数据 */
    public static boolean allows(Long userId) {
        Set<Long> allowed = ALLOWED_USER_IDS.get();
        if (allowed == null) {
            return true;
        }
        return userId != null && allowed.contains(userId);
    }

    public static void clear() {
        ALLOWED_USER_IDS.remove();
    }
}
