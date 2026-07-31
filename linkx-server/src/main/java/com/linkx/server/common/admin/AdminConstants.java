package com.linkx.server.common.admin;

/**
 * 管理端角色与模块常量。
 */
public final class AdminConstants {

    private AdminConstants() {
    }

    /** 可登录管理端的角色 */
    public static final String[] ADMIN_ROLES = {"admin", "super_admin"};

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    /** CSV 导出单次最大行数 */
    public static final int EXPORT_MAX_SIZE = 5000;
}
