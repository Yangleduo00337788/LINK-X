package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.DataScopeContext;

import java.util.Set;

/**
 * 数据权限过滤辅助。
 */
public final class DataScopeFilters {

    private DataScopeFilters() {
    }

    /** {@code null} 表示不限制 */
    public static Set<Long> allowedUserIds() {
        return DataScopeContext.getAllowedUserIds();
    }

    public static boolean allows(Long userId) {
        return DataScopeContext.allows(userId);
    }
}
