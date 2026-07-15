package com.linkx.server.support;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 内嵌 Redis 单例，供集成测试使用。
 * <p>
 * 由于 {@code @DynamicPropertySource} 在 Spring 上下文创建期间执行（早于 {@code @BeforeAll}），
 * 这里在首次访问时懒启动一个真实的 redis-server 进程，并在 JVM 退出时关闭。
 * 端口自动选取空闲端口，避免与本机已有 Redis 冲突。
 */
public final class EmbeddedRedis {

    private static RedisServer server;
    private static int port;

    private EmbeddedRedis() {
    }

    public static synchronized int startIfNeeded() {
        if (server != null) {
            return port;
        }
        port = findFreePort();
        try {
            server = RedisServer.newRedisServer()
                    .port(port)
                    .setting("bind 127.0.0.1")
                    // 限制内存使用（embedded-redis 1.4.3 已移除 maxheap 配置项，使用 maxmemory 兼容）
                    .setting("maxmemory 100mb")
                    .build();
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("无法启动内嵌 Redis (port=" + port + ")", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // JVM 退出时忽略关闭异常
            }
        }));
        return port;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法分配空闲端口用于内嵌 Redis", e);
        }
    }
}
