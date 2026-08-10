package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
/**
 * RBAC 权限体系常量定义。
 * <p>
 * 集中管理角色编码、权限通配符、资源类型与 Redis 缓存 key 前缀，
 * 避免魔法字符串散落各处，符合 DRY 原则。
 * </p>
 */
public final class RbacConstants {

    private RbacConstants() {
    }

    /** 系统管理员角色编码，拥有全部权限 */
    public static final String ROLE_ADMIN = "admin";

    /** 普通用户角色编码，注册时默认分配 */
    public static final String ROLE_USER = "user";

    /**
     * 超级管理员角色编码（受保护）：
     * - 仅可由更高权限角色授予；
     * - 不可被 grant 给自己，防止意外提权。
     */
    public static final String ROLE_SUPER_ADMIN = "super_admin";

    /**
     * 当前进程的最高角色编码（数值越小权限越高，用于判断 grant 方向合法性）。
     */
    public static final int ROLE_LEVEL_SUPER_ADMIN = 0;
    public static final int ROLE_LEVEL_ADMIN = 100;
    public static final int ROLE_LEVEL_USER = 1000;

    /** 权限通配符，匹配全部权限（仅 admin 持有） */
    public static final String PERM_ALL = "*";

    /** 资源类型：菜单 */
    public static final String RESOURCE_MENU = "menu";

    /** 资源类型：按钮 */
    public static final String RESOURCE_BUTTON = "button";

    /** 资源类型：API 接口 */
    public static final String RESOURCE_API = "api";

    /** 用户角色缓存 key 前缀，完整 key = linkx:user:roles:{userId} */
    public static final String CACHE_KEY_ROLES = "linkx:user:roles:";

    /** 用户权限缓存 key 前缀，完整 key = linkx:user:perms:{userId} */
    public static final String CACHE_KEY_PERMS = "linkx:user:perms:";

    /** 角色变更后清除缓存的重试最大次数 */
    public static final long CACHE_TTL_MINUTES = 30;
}
