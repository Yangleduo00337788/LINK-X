package com.linkx.server.common.admin;

import java.util.Collection;

/**
 * 管理端角色与模块常量。
 */
public final class AdminConstants {

    private AdminConstants() {
    }

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_OPS_ADMIN = "ops_admin";
    public static final String ROLE_AUDIT_ADMIN = "audit_admin";
    public static final String ROLE_SECURITY_ADMIN = "security_admin";
    public static final String ROLE_READONLY_OBSERVER = "readonly_observer";

    /** 可登录管理端的角色（超管 / 运营 / 审核 / 安全 / 只读） */
    public static final String[] ADMIN_ROLES = {
            ROLE_ADMIN,
            ROLE_SUPER_ADMIN,
            ROLE_OPS_ADMIN,
            ROLE_AUDIT_ADMIN,
            ROLE_SECURITY_ADMIN,
            ROLE_READONLY_OBSERVER
    };

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    /** CSV 导出单次最大行数 */
    public static final int EXPORT_MAX_SIZE = 5000;

    /** 是否拥有任一可登录管理端的角色。 */
    public static boolean hasAdminPortalRole(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String required : ADMIN_ROLES) {
            if (roles.contains(required)) {
                return true;
            }
        }
        return false;
    }

    /** 是否可编辑其他管理端账号的资料（含部门归属）。冻/封等状态操作仍禁止。 */
    public static boolean canEditAdminProfiles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_SUPER_ADMIN);
    }
}
