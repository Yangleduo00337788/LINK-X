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
        private Long accessExpire = 7_200_000L;
        private Long refreshExpire = 604_800_000L;
    }

    @Data
    public static class Auth {
        private boolean captchaEnabled = true;
        private int loginMaxAttempts = 5;
        private int lockDurationMinutes = 15;
        private int rateLimitLoginPerMinute = 10;
        private int rateLimitRegisterPerMinute = 5;
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
}
