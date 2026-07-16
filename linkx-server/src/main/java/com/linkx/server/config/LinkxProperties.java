package com.linkx.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * LinkX 自定义配置项，对应 application.yml 中 linkx 节点。
 */
@Data
@ConfigurationProperties(prefix = "linkx")
public class LinkxProperties {

    private final Jwt jwt = new Jwt();
    private final Auth auth = new Auth();
    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Minio minio = new Minio();
    private final Im im = new Im();
    private final Proxy proxy = new Proxy();

    @Data
    public static class Im {
        private int websocketPort = 8081;
        private String websocketPath = "/ws";
        private int heartbeatIntervalSeconds = 30;
    }

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin123";
        private String bucketName = "linkx";
        private long maxFileSize = 10 * 1024 * 1024; // 默认10MB
    }

    @Data
    public static class Jwt {
        private String secret;
        /** access token TTL：默认 30 分钟（毫秒） */
        private Long accessExpire = 1_800_000L;
        /** refresh token TTL：默认 3 天（毫秒） */
        private Long refreshExpire = 259_200_000L;
    }

    @Data
    public static class Auth {
        private boolean captchaEnabled = true;
        private int loginMaxAttempts = 5;
        private int lockDurationMinutes = 15;
        private int rateLimitLoginPerMinute = 10;
        private int rateLimitRegisterPerMinute = 5;
        // 业务接口默认限流配置
        private int rateLimitSearchPerMinute = 30;
        private int rateLimitListPerMinute = 60;
        private int rateLimitWritePerMinute = 30;
        private int rateLimitUploadPerMinute = 20;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class Security {
        /** 生产环境可开启 HTTPS 强制（本地开发保持 false） */
        private boolean requireHttps = false;
    }

    /**
     * 反向代理配置：仅当部署在 Nginx/Cloudflare 等反向代理后面时才应启用 trustProxy。
     * 启用后服务端会信任 X-Forwarded-For/X-Real-IP 头来解析客户端真实 IP；
     * 未启用时一律使用 socket.getRemoteAddr()，避免攻击者伪造 IP 绕过限流。
     */
    @Data
    public static class Proxy {
        /** 是否信任反向代理转发的客户端 IP 头（默认 false，安全优先） */
        private boolean trustProxy = false;
        /** 仅在 trustProxy=true 时生效：允许信任的反代 IP 段，留空表示信任所有（不推荐） */
        private List<String> trustedIps = new ArrayList<>();
    }
}
