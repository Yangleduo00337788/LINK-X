package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "系统配置视图（管理端 / 客户端独立）")
public class AdminSettingVO {

    @Schema(description = "注册配置（客户端）")
    private RegisterSide register;

    @Schema(description = "登录配置（客户端 + 管理端）")
    private LoginSide login;

    @Schema(description = "密码策略（管理端 / 客户端共用）")
    private PasswordSide password;

    @Schema(description = "管理端配置（兼容旧字段，验证码请看 login）")
    private AdminSide admin;

    @Schema(description = "客户端配置")
    private ClientSide client;

    @Schema(description = "邮件 SMTP 配置")
    private MailSide mail;

    @Schema(description = "邮件模板配置")
    private MailTemplatesSide mailTemplates;

    @Schema(description = "安全配置（管理端）")
    private SecuritySide security;

    @Schema(description = "对象存储配置")
    private StorageSide storage;

    @Schema(description = "灵伴（LinkMate）AI 配置")
    private LinkMateSide linkmate;

    @Data
    @Builder
    @Schema(description = "灵伴 AI 配置")
    public static class LinkMateSide {
        @Schema(description = "是否启用")
        private Boolean enabled;
        @Schema(description = "API 基址")
        private String baseUrl;
        @Schema(description = "模型名称")
        private String model;
        @Schema(description = "单次最大生成 token")
        private Integer maxTokens;
        @Schema(description = "采样温度")
        private Double temperature;
        @Schema(description = "单用户每日 token 上限")
        private Integer dailyTokenLimit;
        @Schema(description = "API Key 是否已配置（不回传明文）")
        private Boolean apiKeyConfigured;
        @Schema(description = "系统提示词")
        private String systemPrompt;
        @Schema(description = "当前模型是否支持深度思考")
        private Boolean reasoningSupported;
        @Schema(description = "语音转写 API 基址")
        private String sttBaseUrl;
        @Schema(description = "语音转写模型")
        private String sttModel;
        @Schema(description = "语音转写 API Key 是否已配置（不回传明文）")
        private Boolean sttApiKeyConfigured;
        @Schema(description = "Realtime API 基址")
        private String realtimeBaseUrl;
        @Schema(description = "Realtime 模型")
        private String realtimeModel;
        @Schema(description = "Realtime 音色")
        private String realtimeVoice;
        @Schema(description = "Realtime API Key 是否已配置（不回传明文）")
        private Boolean realtimeApiKeyConfigured;
        @Schema(description = "是否允许客户端使用 Agent 模式")
        private Boolean agentEnabled;
        @Schema(description = "群 AI 新建群默认策略")
        private GroupAiDefaultsSide groupAiDefaults;
        @Schema(description = "群 AI 功能启用概览（只读）")
        private GroupAiOverviewSide groupAiOverview;
    }

    @Data
    @Builder
    @Schema(description = "群 AI 新建群默认策略")
    public static class GroupAiDefaultsSide {
        @Schema(description = "新建群是否默认开启灵伴")
        private Boolean linkmateEnabled;
        @Schema(description = "新建群是否默认开启主动发言")
        private Boolean proactiveEnabled;
        @Schema(description = "新建群是否默认开启智能总结")
        private Boolean smartSummaryEnabled;
        @Schema(description = "新建群默认关注话题")
        private String interestTopics;
        @Schema(description = "新建群默认总结指令")
        private String summaryInstruction;
    }

    @Data
    @Builder
    @Schema(description = "群 AI 功能启用概览")
    public static class GroupAiOverviewSide {
        @Schema(description = "群聊总数")
        private Long totalGroups;
        @Schema(description = "已开启灵伴的群数")
        private Long linkmateEnabledGroups;
        @Schema(description = "已开启主动发言的群数")
        private Long proactiveEnabledGroups;
        @Schema(description = "已开启智能总结的群数")
        private Long smartSummaryEnabledGroups;
    }

    @Data
    @Builder
    @Schema(description = "安全配置")
    public static class SecuritySide {
        @Schema(description = "是否禁止前端调试")
        private Boolean disableFrontendDebug;
        @Schema(description = "是否启用 API 请求签名")
        private Boolean apiSignEnabled;
        @Schema(description = "是否启用 API 请求/响应体加密")
        private Boolean apiEncryptEnabled;
    }

    @Data
    @Builder
    @Schema(description = "注册配置")
    public static class RegisterSide {
        @Schema(description = "客户端是否开放注册")
        private Boolean registerEnabled;
        @Schema(description = "忘记密码邮箱验证是否启用")
        private Boolean forgotPasswordEmailEnabled;
    }

    @Data
    @Builder
    @Schema(description = "登录配置")
    public static class LoginSide {
        private LoginEntry client;
        private LoginEntry admin;
    }

    @Data
    @Builder
    @Schema(description = "单侧登录配置")
    public static class LoginEntry {
        @Schema(description = "是否开启验证码")
        private Boolean captchaEnabled;
        @Schema(description = "验证码类型：image | slider")
        private String captchaType;
        @Schema(description = "登录失败最大重试次数")
        private Integer maxAttempts;
        @Schema(description = "自动封禁时长（分钟）")
        private Integer lockDurationMinutes;

        @Schema(description = "是否强制开启 TOTP 2FA（仅管理端）")
        private Boolean totpRequired;
    }

    @Data
    @Builder
    @Schema(description = "密码策略")
    public static class PasswordSide {
        @Schema(description = "最小长度")
        private Integer minLength;
        @Schema(description = "最大长度")
        private Integer maxLength;
        @Schema(description = "是否必须同时包含大小写字母")
        private Boolean requireUpperLower;
        @Schema(description = "是否必须包含数字")
        private Boolean requireDigit;
        @Schema(description = "是否必须包含特殊字符")
        private Boolean requireSpecial;
    }

    @Data
    @Builder
    @Schema(description = "管理端侧配置")
    public static class AdminSide {
        @Schema(description = "管理端登录验证码")
        private Boolean captchaEnabled;
    }

    @Data
    @Builder
    @Schema(description = "客户端侧配置")
    public static class ClientSide {
        @Schema(description = "客户端登录/注册验证码")
        private Boolean captchaEnabled;
        private String appVersion;
        private String appChannel;
        private String releaseNotes;
        private String downloadUrl;
        @Schema(description = "有可用更新时是否强制升级")
        private Boolean forceUpdate;
        @Schema(description = "最低支持版本（低于此版本强制升级）")
        private String minSupportedVersion;
        private Long maxUploadBytes;
        @Schema(description = "敏感词过滤总开关")
        private Boolean sensitiveFilterEnabled;
        @Schema(description = "客服邮箱")
        private String supportEmail;
        @Schema(description = "客服电话")
        private String supportPhone;
        @Schema(description = "反馈处理 SLA（小时）")
        private Integer feedbackSlaHours;
        @Schema(description = "是否启用反馈超时升级")
        private Boolean feedbackEscalationEnabled;
        @Schema(description = "升级时尝试按分流规则自动改派")
        private Boolean feedbackEscalationAutoReassign;
        @Schema(description = "同一工单重复升级间隔（小时）")
        private Integer feedbackEscalationIntervalHours;
        @Schema(description = "审核任务 SLA（小时）")
        private Integer reviewSlaHours;
        @Schema(description = "是否启用审核超时督办")
        private Boolean reviewEscalationEnabled;
        @Schema(description = "同一审核任务重复督办间隔（小时）")
        private Integer reviewEscalationIntervalHours;
    }

    @Data
    @Builder
    @Schema(description = "邮件 SMTP 配置")
    public static class MailSide {
        private String host;
        private Integer port;
        private String username;
        @Schema(description = "是否已配置授权码（不回传明文）")
        private Boolean passwordConfigured;
        private String from;
        private String fromName;
        private Boolean startTls;
        private Boolean ssl;
        private Integer codeExpireMinutes;
    }

    @Data
    @Builder
    @Schema(description = "邮件模板")
    public static class MailTemplatesSide {
        private MailTemplate register;
        private MailTemplate reset;
        private MailTemplate welcome;
    }

    @Data
    @Builder
    @Schema(description = "对象存储配置")
    public static class StorageSide {
        @Schema(description = "minio | oss | cos | r2")
        private String provider;
        private String minioEndpoint;
        private String minioBucketName;
        private String minioAccessKey;
        @Schema(description = "MinIO Secret 是否已配置")
        private Boolean minioSecretConfigured;
        private String ossEndpoint;
        private String ossBucketName;
        private String ossAccessKeyId;
        @Schema(description = "OSS Secret 是否已配置")
        private Boolean ossAccessKeySecretConfigured;
        private String ossCnameDomain;
        private String cosRegion;
        private String cosBucketName;
        private String cosSecretId;
        @Schema(description = "COS SecretKey 是否已配置")
        private Boolean cosSecretKeyConfigured;
        private String cosCnameDomain;
        private String r2Endpoint;
        private String r2BucketName;
        private String r2AccessKeyId;
        @Schema(description = "R2 Secret Access Key 是否已配置")
        private Boolean r2SecretAccessKeyConfigured;
        private String r2CnameDomain;
        private Long maxUploadBytes;
        private Integer presignAvatarSeconds;
        private Integer presignFileSeconds;
        private Integer presignShareSeconds;
    }

    @Data
    @Builder
    @Schema(description = "单封邮件模板（HTML 正文；空自定义时返回默认）")
    public static class MailTemplate {
        private String subject;
        @Schema(description = "HTML 正文")
        private String html;
        @Schema(description = "是否使用内置默认模板")
        private Boolean usingDefault;
    }
}
