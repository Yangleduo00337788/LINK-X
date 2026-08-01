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
    private final Mail mail = new Mail();
    private final MailTemplates mailTemplates = new MailTemplates();
    private final App app = new App();
    private final Retention retention = new Retention();
    private final IpGeo ipGeo = new IpGeo();

    @Data
    public static class Im {
        private int websocketPort = 8081;
        private String websocketPath = "/ws";
        private int heartbeatIntervalSeconds = 30;
        /**
         * 客户端 sync 动作单次拉取的离线消息上限。
         * 客户端通过 lastServerMsgId 游标分页，超过上限时响应 hasMore=true，
         * 客户端可再次发起 sync 拉取剩余消息。
         */
        private int syncBatchSize = 200;

        public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = Math.max(10, heartbeatIntervalSeconds);
        }
    }

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        /** 必填，须通过环境变量注入，禁止硬编码默认值 */
        private String accessKey;
        /** 必填，须通过环境变量注入，禁止硬编码默认值 */
        private String secretKey;
        private String bucketName = "linkx";
        private long maxFileSize = 10 * 1024 * 1024; // 默认10MB
        /** 预签名 URL 分级过期（秒） */
        private final PresignExpiry presignExpiry = new PresignExpiry();
    }

    /**
     * MinIO 预签名分级：头像较长便于列表展示；文件较短；分享最短。
     */
    @Data
    public static class PresignExpiry {
        /** 头像 / 封面 / 友链配图，默认 24 小时 */
        private int avatarSeconds = 24 * 3600;
        /** 聊天/群/网盘业务文件，默认 1 小时 */
        private int fileSeconds = 3600;
        /** 外部分享下载，默认 10 分钟 */
        private int shareSeconds = 600;
    }

    @Data
    public static class Jwt {
        private String secret;
        /** access token TTL：默认 30 分钟（毫秒） */
        private Long accessExpire = 1_800_000L;
        /** refresh token TTL：默认 3 天（毫秒） */
        private Long refreshExpire = 259_200_000L;

        public void setAccessExpire(Long accessExpire) {
            this.accessExpire = accessExpire == null ? 1_800_000L : Math.max(60_000L, accessExpire);
        }

        public void setRefreshExpire(Long refreshExpire) {
            this.refreshExpire = refreshExpire == null ? 259_200_000L : Math.max(3600_000L, refreshExpire);
        }
    }

    @Data
    public static class Auth {
        /** 客户端登录/注册图形验证码 */
        private boolean captchaEnabled = true;
        /** 管理端登录图形验证码（可与客户端独立开关） */
        private boolean adminCaptchaEnabled = true;
        /** 客户端是否开放注册 */
        private boolean registerEnabled = true;
        /** 忘记密码邮箱验证是否启用 */
        private boolean forgotPasswordEmailEnabled = true;
        /** 客户端登录失败最大次数 */
        private int loginMaxAttempts = 5;
        /** 管理端登录失败最大次数 */
        private int adminLoginMaxAttempts = 5;
        /** 客户端自动封禁时长（分钟），默认 10 */
        private int lockDurationMinutes = 10;
        /** 管理端自动封禁时长（分钟），默认 10 */
        private int adminLockDurationMinutes = 10;
        /** 管理端是否强制要求开启 TOTP 2FA */
        private boolean adminTotpRequired = false;
        /** 管理端高危操作是否启用二次验证（TOTP/邮箱） */
        private boolean adminStepUpEnabled = true;
        /** 密码最小长度（管理端/客户端共用） */
        private int passwordMinLength = 8;
        /** 密码最大长度 */
        private int passwordMaxLength = 64;
        /** 是否必须同时包含大小写字母 */
        private boolean passwordRequireUpperLower = false;
        /** 是否必须包含数字（默认 true，与历史规则一致） */
        private boolean passwordRequireDigit = true;
        /** 是否必须包含特殊字符 */
        private boolean passwordRequireSpecial = false;
        private int rateLimitLoginPerMinute = 10;
        private int rateLimitRegisterPerMinute = 5;
        // 业务接口默认限流配置
        private int rateLimitSearchPerMinute = 30;
        private int rateLimitListPerMinute = 60;
        private int rateLimitWritePerMinute = 30;
        private int rateLimitUploadPerMinute = 20;

        public void setLoginMaxAttempts(int loginMaxAttempts) {
            this.loginMaxAttempts = Math.max(1, loginMaxAttempts);
        }

        public void setAdminLoginMaxAttempts(int adminLoginMaxAttempts) {
            this.adminLoginMaxAttempts = Math.max(1, adminLoginMaxAttempts);
        }

        public void setLockDurationMinutes(int lockDurationMinutes) {
            this.lockDurationMinutes = Math.max(1, lockDurationMinutes);
        }

        public void setAdminLockDurationMinutes(int adminLockDurationMinutes) {
            this.adminLockDurationMinutes = Math.max(1, adminLockDurationMinutes);
        }

        public void setPasswordMinLength(int passwordMinLength) {
            this.passwordMinLength = Math.max(4, Math.min(128, passwordMinLength));
            if (this.passwordMaxLength < this.passwordMinLength) {
                this.passwordMaxLength = this.passwordMinLength;
            }
        }

        public void setPasswordMaxLength(int passwordMaxLength) {
            this.passwordMaxLength = Math.max(4, Math.min(128, passwordMaxLength));
            if (this.passwordMaxLength < this.passwordMinLength) {
                this.passwordMinLength = this.passwordMaxLength;
            }
        }
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 兼容 env 逗号分隔写法：CORS_ALLOWED_ORIGINS=a,b,c
         * （YAML 标量绑定时可能先变成单元素 List，这里再拆分）
         */
        public void setAllowedOrigins(List<String> origins) {
            this.allowedOrigins = normalizeOrigins(origins);
        }

        public boolean hasAllowedOrigins() {
            return allowedOrigins != null && !allowedOrigins.isEmpty();
        }

        private static List<String> normalizeOrigins(List<String> origins) {
            if (origins == null || origins.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> out = new ArrayList<>();
            for (String item : origins) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                for (String part : item.split(",")) {
                    String origin = part.trim();
                    if (!origin.isEmpty() && !out.contains(origin)) {
                        out.add(origin);
                    }
                }
            }
            return out;
        }
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

        public void setTrustedIps(List<String> trustedIps) {
            if (trustedIps == null) {
                this.trustedIps = new ArrayList<>();
                return;
            }
            List<String> out = new ArrayList<>();
            for (String ip : trustedIps) {
                if (ip != null && !ip.isBlank()) {
                    out.add(ip.trim());
                }
            }
            this.trustedIps = out;
        }
    }

    /**
     * 邮件服务配置
     */
    @Data
    public static class Mail {
        /** 发件人邮箱地址 */
        private String from = "noreply@linkx.com";
        /** 发件人名称 */
        private String fromName = "LinkX";
        /** SMTP 服务器地址 */
        private String host = "smtp.example.com";
        /** SMTP 端口 */
        private int port = 587;
        /** 邮箱用户名 */
        private String username = "";
        /** 邮箱密码或授权码 */
        private String password = "";
        /** 是否启用 STARTTLS（587 端口必为 true） */
        private boolean startTls = true;
        /** 是否启用 SSL 直连（仅 465 端口需要 true） */
        private boolean ssl = false;
        /** 验证码有效期（分钟） */
        private int codeExpireMinutes = 10;
    }

    /**
     * 可自定义邮件模板（空则使用内置默认）。
     * 占位符：${USERNAME} ${NICKNAME} ${EMAIL} ${CODE} ${EXPIRE_MINUTES} ${YEAR}
     */
    @Data
    public static class MailTemplates {
        private String registerSubject = "";
        private String registerHtml = "";
        private String resetSubject = "";
        private String resetHtml = "";
        private String welcomeSubject = "";
        private String welcomeHtml = "";
    }

    /**
     * 应用自身配置（用于"检查更新"等接口）。
     * 通过 linkx.app.* 在 application.yml 覆盖。
     */
    @Data
    public static class App {
        /** 当前服务端版本（与客户端构建版本号一致时视为最新） */
        private String version = "1.0.0";
        /** 发布渠道，用于灰度控制 */
        private String channel = "stable";
        /** 升级提示信息 */
        private String releaseNotes = "当前已是最新版本";
        /** 下载地址（可空） */
        private String downloadUrl = "";
        /** 有可用更新时是否强制升级 */
        private Boolean forceUpdate = false;
        /** 低于此版本强制升级（可空；空表示不额外按最低版本强更） */
        private String minSupportedVersion = "";
        /** 敏感词过滤总开关（关闭后业务侧不再拦截） */
        private Boolean sensitiveFilterEnabled = true;
        /** 客服邮箱 */
        private String supportEmail = "";
        /** 客服电话 */
        private String supportPhone = "";
        /** 反馈处理 SLA（小时），超时未回复视为逾期 */
        private Integer feedbackSlaHours = 24;
        /** 开发模式：允许 localhost origin 连接 WebSocket（生产环境必须关闭） */
        private Boolean devModeEnabled = false;
    }

    /**
     * 数据留存策略。message-days &lt;= 0 表示不自动清理。
     */
    @Data
    public static class Retention {
        /** 消息保留天数，默认 365 */
        private int messageDays = 365;

        public void setMessageDays(int messageDays) {
            this.messageDays = messageDays;
        }
    }

    /**
     * IP 归属地：优先 classpath {@code ip2region/ip2region.xdb}，否则读 {@code xdb-path}。
     */
    @Data
    public static class IpGeo {
        /** 外部 xdb 绝对/相对路径（可空） */
        private String xdbPath = "";
    }
}
