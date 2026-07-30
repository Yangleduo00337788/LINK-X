package com.linkx.server.common;

/**
 * 登录入口侧：客户端与管理端使用独立的失败次数 / 锁定时长配置。
 */
public enum LoginSide {
    CLIENT,
    ADMIN
}
