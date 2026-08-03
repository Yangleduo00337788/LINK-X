package com.linkx.server.controller.admin.vo;

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
        @Schema(description = "是否开启图形验证码")
        private Boolean captchaEnabled;
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
    @Schema(description = "单封邮件模板（HTML 正文；空自定义时返回默认）")
    public static class MailTemplate {
        private String subject;
        @Schema(description = "HTML 正文")
        private String html;
        @Schema(description = "是否使用内置默认模板")
        private Boolean usingDefault;
    }
}
