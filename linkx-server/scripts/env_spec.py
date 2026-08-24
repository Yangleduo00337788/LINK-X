"""application.yml 与 .env.* 的 108 项键名、分段与逐行注释规范。"""

from __future__ import annotations

LOCAL_HEADER = """# =============================================================================
# LinkX 后端 · 本地环境（.env.local）
# -----------------------------------------------------------------------------
# 与 application.yml 一一对应（108 项）；已 gitignore
# ============================================================================="""

PROD_HEADER = """# =============================================================================
# LinkX 后端 · 生产环境（.env.prod）
# -----------------------------------------------------------------------------
# 与 application.yml 一一对应（108 项）；已 gitignore
# ============================================================================="""

LOCAL_EXAMPLE_HEADER = """# =============================================================================
# LinkX 后端 · 本地模板（.env.local.example）
# -----------------------------------------------------------------------------
# 复制为 .env.local 后填写真实值；.env.local 已 gitignore
# ============================================================================="""

PROD_EXAMPLE_HEADER = """# =============================================================================
# LinkX 后端 · 生产模板（.env.prod.example）
# -----------------------------------------------------------------------------
# 复制为 .env.prod 后填写；.env.prod 已 gitignore
# ============================================================================="""

# (section_title, key, comment_lines, local_default, prod_default)
# comment_lines: list[str] printed before KEY= (each prefixed with # )
ENV_ENTRIES: list[tuple[str | None, str, list[str], str, str]] = [
    ("Profile / 端口", "SPRING_PROFILES_ACTIVE", ["选择加载 .env.local / .env.prod；对应 yml：spring.profiles.active"], "local", "prod"),
    (None, "SERVER_PORT", ["HTTP API 端口；对应 yml：server.port"], "8080", "8080"),

    ("JWT", "JWT_SECRET", [
        "签名密钥 ≥32 字符；openssl rand -base64 32 或 PowerShell 生成",
        "留空将导致启动校验失败",
    ], "", ""),
    (None, "JWT_ACCESS_EXPIRE", ["access 有效期毫秒（本地 2h）；对应 yml：linkx.jwt.access-expire"], "7200000", "3600000"),
    (None, "JWT_REFRESH_EXPIRE", ["refresh 有效期毫秒（7d）；对应 yml：linkx.jwt.refresh-expire"], "604800000", "604800000"),

    ("消息落库加密", "MESSAGE_CONTENT_ENCRYPT_ENABLED", [
        "是否对 im_message.content / quote_content 落库 AES-256-GCM 加密",
        "对应 yml：linkx.message-encryption.enabled",
    ], "false", "true"),
    (None, "MESSAGE_KEK", [
        "主密钥（启用时必填，须独立于 JWT_SECRET）",
        "openssl rand -base64 32 或 ≥32 字符随机串",
    ], "", ""),
    (None, "MESSAGE_KEK_LEGACY_MAP", [
        "历史 KEK JSON（仅解密旧密文）",
        "轮换示例：{\"default\":\"旧KEK材料\"}",
    ], "", ""),
    (None, "MESSAGE_KEK_KEY_ID", ["密钥版本标识（轮换时改为新 id，如 v2）"], "default", "default"),
    (None, "MESSAGE_SEARCH_SCAN_LIMIT", ["加密开启时聊天记录搜索单次扫描条数上限"], "500", "500"),
    (None, "MESSAGE_REENCRYPT_BATCH_SIZE", ["历史明文重加密每批条数（Snail Job message_content_reencrypt）"], "500", "500"),
    (None, "MESSAGE_KEY_ROTATE_BATCH_SIZE", ["密钥轮换重加密每批条数（Snail Job message_content_key_rotate）"], "500", "500"),

    ("MySQL", "DB_URL", ["完整 JDBC URL；对应 yml：spring.datasource.url"], "jdbc:mysql://127.0.0.1:3306/linkx?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false", "jdbc:mysql://YOUR_DB_HOST:3306/linkx?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=true&requireSSL=true"),
    (None, "DB_USERNAME", ["用户名；对应 yml：spring.datasource.username"], "linkx_app", ""),
    (None, "DB_PASSWORD", ["密码；对应 yml：spring.datasource.password"], "", ""),

    ("Redis", "REDIS_HOST", ["主机；对应 yml：spring.data.redis.host"], "127.0.0.1", ""),
    (None, "REDIS_PORT", ["端口；对应 yml：spring.data.redis.port"], "6379", "6379"),
    (None, "REDIS_PASSWORD", ["密码（无则留空）；对应 yml：spring.data.redis.password"], "", ""),
    (None, "REDIS_DATABASE", ["DB 编号；对应 yml：spring.data.redis.database"], "0", "0"),
    (None, "REDIS_TIMEOUT", ["命令超时；对应 yml：spring.data.redis.timeout"], "3s", "3s"),
    (None, "REDIS_CONNECT_TIMEOUT", ["连接超时；对应 yml：spring.data.redis.connect-timeout"], "3s", "3s"),
    (None, "REDIS_LETTUCE_MAX_ACTIVE", ["Lettuce 最大活跃连接；对应 yml：spring.data.redis.lettuce.pool.max-active"], "16", "32"),
    (None, "REDIS_LETTUCE_MAX_IDLE", ["最大空闲"], "8", "16"),
    (None, "REDIS_LETTUCE_MIN_IDLE", ["最小空闲"], "2", "4"),
    (None, "REDIS_LETTUCE_MAX_WAIT", ["获取连接最大等待"], "3s", "3s"),

    ("HikariCP", "HIKARI_MAX_POOL_SIZE", ["最大连接数；对应 yml：spring.datasource.hikari.maximum-pool-size"], "20", "30"),
    (None, "HIKARI_MIN_IDLE", ["最小空闲"], "5", "10"),
    (None, "HIKARI_CONNECTION_TIMEOUT", ["获取连接超时毫秒"], "30000", "30000"),
    (None, "HIKARI_IDLE_TIMEOUT", ["空闲超时毫秒"], "600000", "600000"),
    (None, "HIKARI_MAX_LIFETIME", ["连接最大存活毫秒"], "1800000", "1800000"),

    ("上传体积", "MULTIPART_MAX_FILE_SIZE", [
        "单文件上限；对应 yml：spring.servlet.multipart.max-file-size",
        "本地安装包约 220MB，建议 300MB；生产可收紧",
    ], "300MB", "5MB"),
    (None, "MULTIPART_MAX_REQUEST_SIZE", ["整次 multipart 请求上限"], "310MB", "6MB"),
    (None, "TOMCAT_MAX_HTTP_FORM_POST_SIZE", [
        "Tomcat POST 体上限（与 multipart 不同层；默认仅 2MB）",
        "对应 yml：server.tomcat.max-http-form-post-size",
    ], "310MB", "6MB"),
    (None, "TOMCAT_MAX_PART_COUNT", ["Tomcat multipart part 数上限；-1 表示不限制"], "-1", "-1"),
    (None, "TOMCAT_MAX_SWALLOW_SIZE", ["Tomcat 吞掉超限请求；-1 表示不限制"], "-1", "-1"),

    ("鉴权 / 安全", "CAPTCHA_ENABLED", ["客户端图形验证码；对应 yml：linkx.auth.captcha-enabled"], "false", "true"),
    (None, "ADMIN_CAPTCHA_ENABLED", ["管理端图形验证码；对应 yml：linkx.auth.admin-captcha-enabled（生产强制 true）"], "false", "true"),
    (None, "CLIENT_CAPTCHA_TYPE", ["客户端验证码类型 image|slider"], "image", "image"),
    (None, "ADMIN_CAPTCHA_TYPE", ["管理端验证码类型 image|slider"], "image", "image"),
    (None, "REGISTER_ENABLED", ["客户端开放注册；对应 yml：linkx.auth.register-enabled"], "true", "true"),
    (None, "FORGOT_PASSWORD_EMAIL_ENABLED", ["忘记密码邮箱验证"], "true", "true"),
    (None, "REQUIRE_HTTPS", ["强制 HTTPS；对应 yml：linkx.security.require-https（生产强制 true）"], "false", "true"),
    (None, "TRUST_PROXY", ["信任反代 X-Forwarded-*；对应 yml：linkx.proxy.trust-proxy"], "false", "true"),
    (None, "AUTH_LOGIN_MAX_ATTEMPTS", ["客户端登录失败锁定次数"], "5", "3"),
    (None, "AUTH_ADMIN_LOGIN_MAX_ATTEMPTS", ["管理端登录失败锁定次数"], "5", "3"),
    (None, "AUTH_LOCK_DURATION_MINUTES", ["客户端锁定时长（分钟）"], "10", "10"),
    (None, "AUTH_ADMIN_LOCK_DURATION_MINUTES", ["管理端锁定时长（分钟）"], "10", "30"),
    (None, "ADMIN_TOTP_REQUIRED", ["管理端强制 TOTP 2FA（生产强制 true）"], "false", "true"),
    (None, "AUTH_PASSWORD_MIN_LENGTH", ["密码最小长度"], "8", "10"),
    (None, "AUTH_PASSWORD_MAX_LENGTH", ["密码最大长度"], "64", "64"),
    (None, "AUTH_PASSWORD_REQUIRE_UPPER_LOWER", ["是否必须同时包含大小写字母"], "false", "true"),
    (None, "AUTH_PASSWORD_REQUIRE_DIGIT", ["是否必须包含数字"], "true", "true"),
    (None, "AUTH_PASSWORD_REQUIRE_SPECIAL", ["是否必须包含特殊字符"], "false", "true"),
    (None, "AUTH_RATE_LIMIT_LOGIN", ["登录限流（次/分钟/IP）"], "10", "5"),
    (None, "AUTH_RATE_LIMIT_REGISTER", ["注册限流（次/分钟/IP）"], "5", "2"),

    ("CORS", "CORS_ALLOWED_ORIGINS", ["逗号分隔白名单；对应 yml：linkx.cors.allowed-origins"], "http://localhost:5173,http://127.0.0.1:5173", "https://app.example.com,https://admin.example.com"),

    ("IM", "IM_WS_PORT", ["WebSocket 端口；对应 yml：linkx.im.websocket-port"], "8081", "8081"),

    ("MinIO", "MINIO_ENDPOINT", ["Endpoint；对应 yml：linkx.minio.endpoint"], "http://127.0.0.1:9000", "https://minio.example.com"),
    (None, "MINIO_ACCESS_KEY", ["Access Key；对应 yml：linkx.minio.access-key"], "", ""),
    (None, "MINIO_SECRET_KEY", ["Secret Key；对应 yml：linkx.minio.secret-key"], "", ""),
    (None, "MINIO_BUCKET_NAME", ["桶名"], "linkx", "linkx"),
    (None, "MINIO_MAX_FILE_SIZE", ["单文件字节上限；对应 yml：linkx.minio.max-file-size"], "104857600", "5242880"),
    (None, "MINIO_PRESIGN_AVATAR_SECONDS", ["头像预签名秒数"], "7200", "7200"),
    (None, "MINIO_PRESIGN_FILE_SECONDS", ["文件预签名秒数"], "1800", "900"),
    (None, "MINIO_PRESIGN_SHARE_SECONDS", ["分享预签名秒数"], "600", "300"),

    ("对象存储切换", "STORAGE_PROVIDER", ["当前提供商 minio|oss|cos；管理端可热切换"], "minio", "minio"),
    (None, "OSS_ENDPOINT", ["OSS Endpoint（如 oss-cn-beijing.aliyuncs.com）"], "", ""),
    (None, "OSS_BUCKET_NAME", ["OSS 桶名"], "", ""),
    (None, "OSS_ACCESS_KEY_ID", ["OSS AccessKeyId"], "", ""),
    (None, "OSS_ACCESS_KEY_SECRET", ["OSS AccessKeySecret"], "", ""),
    (None, "OSS_CNAME_DOMAIN", ["OSS 自定义 CNAME（可空）"], "", ""),
    (None, "COS_REGION", ["COS 地域（如 ap-beijing）"], "ap-beijing", "ap-beijing"),
    (None, "COS_BUCKET_NAME", ["COS 桶名（含 AppId）"], "linkx-cos-1361202373", ""),
    (None, "COS_SECRET_ID", ["COS SecretId"], "", ""),
    (None, "COS_SECRET_KEY", ["COS SecretKey"], "", ""),
    (None, "COS_CNAME_DOMAIN", ["COS 自定义 CNAME（可空）"], "", ""),

    ("邮件", "MAIL_HOST", ["SMTP 主机；对应 yml：linkx.mail.host"], "smtp.qq.com", "smtp.qq.com"),
    (None, "MAIL_PORT", ["SMTP 端口"], "587", "587"),
    (None, "MAIL_USERNAME", ["发信账号"], "", ""),
    (None, "MAIL_PASSWORD", ["授权码"], "", ""),
    (None, "MAIL_FROM", ["发件人地址"], "", ""),
    (None, "MAIL_FROM_NAME", ["发件人显示名"], "LinkX", "LinkX"),
    (None, "MAIL_START_TLS", ["STARTTLS"], "true", "true"),
    (None, "MAIL_SSL", ["SSL 直连（465 端口）"], "false", "false"),
    (None, "MAIL_CODE_EXPIRE_MINUTES", ["验证码有效分钟"], "10", "10"),

    ("应用 / 留存", "APP_VERSION", ["版本；对应 yml：linkx.app.version"], "1.0.0", "1.0.0"),
    (None, "APP_CHANNEL", ["发布渠道"], "stable", "stable"),
    (None, "APP_RELEASE_NOTES", ["更新说明"], "本地开发构建", ""),
    (None, "APP_DOWNLOAD_URL", ["下载地址"], "", ""),
    (None, "APP_FORCE_UPDATE", ["有更新时是否强制升级"], "false", "false"),
    (None, "APP_MIN_SUPPORTED_VERSION", ["最低支持版本（低于则强更；可空）"], "", ""),
    (None, "DEV_MODE_ENABLED", ["开发模式：允许 localhost 连 WS（生产必须 false）"], "true", "false"),
    (None, "MESSAGE_RETENTION_DAYS", ["消息保留天数；对应 yml：linkx.retention.message-days"], "365", "365"),
    (None, "IP_GEO_XDB_PATH", ["可选：外部 ip2region.xdb 路径（可空，默认 classpath）"], "", ""),

    ("Swagger / Actuator / 日志", "SPRINGDOC_ENABLED", ["是否开启 Swagger；对应 yml：springdoc.*.enabled"], "true", "false"),
    (None, "MANAGEMENT_ENDPOINTS", ["Actuator 暴露端点"], "health,info,metrics,prometheus", "health"),
    (None, "MANAGEMENT_HEALTH_SHOW_DETAILS", ["健康详情策略"], "when_authorized", "never"),
    (None, "LOG_LEVEL_ROOT", ["根日志级别"], "info", "INFO"),
    (None, "LOG_LEVEL_LINKX", ["业务包日志级别"], "debug", "INFO"),
    (None, "LOG_LEVEL_SECURITY", ["Security 日志级别"], "WARN", "WARN"),

    ("SnailJob 分布式调度", "SNAIL_JOB_ENABLED", ["是否启用 SnailJob 客户端；对应 yml：snail-job.enabled"], "true", "true"),
    (None, "SNAIL_JOB_SYNC_JOBS", ["启动时注册内置任务；对应 yml：linkx.snail-job.sync-jobs-on-startup"], "true", "true"),
    (None, "SNAIL_JOB_SERVER_HOST", ["SnailJob gRPC 主机"], "127.0.0.1", ""),
    (None, "SNAIL_JOB_SERVER_PORT", ["SnailJob gRPC 端口"], "17888", "17888"),
    (None, "SNAIL_JOB_ADMIN_URL", ["控制台 Web 地址（管理端跳转）"], "http://127.0.0.1:18080", ""),
    (None, "SNAIL_JOB_OPENAPI_HOST", ["OpenAPI 主机"], "127.0.0.1", ""),
    (None, "SNAIL_JOB_OPENAPI_PORT", ["OpenAPI 端口"], "18080", "18080"),
    (None, "SNAIL_JOB_NAMESPACE", ["命名空间（须在控制台创建）"], "764d604ec6fc45f68cd92514c40e9e1a", ""),
    (None, "SNAIL_JOB_GROUP", ["客户端组"], "linkx_server", "linkx_server"),
    (None, "SNAIL_JOB_TOKEN", ["客户端 Token（须在控制台创建，禁止用仓库示例值上生产）"], "SJ_Wyz3dmsdbDOkDujOTSSoBjGQP1BMsVnj", ""),
    (None, "SNAIL_JOB_CLIENT_HOST", ["客户端对外 host（多实例可留空）"], "", ""),
    (None, "SNAIL_JOB_CLIENT_PORT", ["客户端端口"], "17889", "17889"),
]

ORDER = [entry[1] for entry in ENV_ENTRIES]
