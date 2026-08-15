package com.linkx.server.service.customerservice;

import java.util.Objects;

/**
 * 运行时客服机器人用户 ID（启动时由 Bootstrap 写入）。
 */
public final class CustomerServiceRegistry {

    private static volatile Long botUserId;

    private CustomerServiceRegistry() {
    }

    public static void setBotUserId(Long id) {
        botUserId = id;
    }

    public static Long botUserId() {
        return botUserId;
    }

    public static boolean isBot(Long userId) {
        return userId != null && Objects.equals(userId, botUserId);
    }
}
