package com.linkx.server.entity;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统运行时配置（单行表，id 恒为 1）。
 * 管理端与客户端配置独立字段，修改后立即覆盖 {@link com.linkx.server.config.LinkxProperties}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_runtime_setting")
public class SysRuntimeSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** 管理端登录验证码 */
    private Boolean adminCaptchaEnabled;
    /** 管理端验证码类型：image | slider */
    private String adminCaptchaType;
    /** 管理端登录失败最大次数 */
    private Integer adminLoginMaxAttempts;
    /** 管理端自动封禁分钟数 */
    private Integer adminLockDurationMinutes;
    /** 管理端是否强制开启 2FA */
    private Boolean adminTotpRequired;
    /** 客户端登录/注册验证码 */
    private Boolean clientCaptchaEnabled;
    /** 客户端验证码类型：image | slider */
    private String clientCaptchaType;
    /** 客户端是否开放注册 */
    private Boolean clientRegisterEnabled;
    /** 忘记密码邮箱验证是否启用 */
    private Boolean clientForgotPasswordEmailEnabled;
    /** 客户端登录失败最大次数 */
    private Integer clientLoginMaxAttempts;
    /** 客户端自动封禁分钟数 */
    private Integer clientLockDurationMinutes;
    /** 密码最小长度 */
    private Integer passwordMinLength;
    /** 密码最大长度 */
    private Integer passwordMaxLength;
    /** 是否必须同时包含大小写字母 */
    private Boolean passwordRequireUpperLower;
    /** 是否必须包含数字 */
    private Boolean passwordRequireDigit;
    /** 是否必须包含特殊字符 */
    private Boolean passwordRequireSpecial;

    private String appVersion;
    private String appChannel;
    private String releaseNotes;
    private String downloadUrl;
    /** 有更新时是否强制升级 */
    private Boolean forceUpdate;
    /** 低于此版本强制升级（可空） */
    private String minSupportedVersion;
    private Long maxUploadBytes;
    /** 对象存储提供商：minio | oss | local */
    private String storageProvider;
    private String minioEndpoint;
    private String minioBucketName;
    private String minioAccessKey;
    private String minioSecretKey;
    private String ossEndpoint;
    private String ossBucketName;
    private String ossAccessKeyId;
    private String ossAccessKeySecret;
    private String ossCnameDomain;
    private String localStoragePath;
    /** 敏感词过滤总开关 */
    private Boolean sensitiveFilterEnabled;
    /** 客服邮箱 */
    private String supportEmail;
    /** 客服电话 */
    private String supportPhone;
    /** 反馈处理 SLA（小时），超时未回复视为逾期 */
    private Integer feedbackSlaHours;
    /** 是否启用反馈超时升级 */
    private Boolean feedbackEscalationEnabled;
    /** 升级时尝试按分流规则自动改派 */
    private Boolean feedbackEscalationAutoReassign;
    /** 同一工单重复升级间隔（小时） */
    private Integer feedbackEscalationIntervalHours;
    /** 审核任务 SLA（小时） */
    private Integer reviewSlaHours;
    /** 是否启用审核超时督办 */
    private Boolean reviewEscalationEnabled;
    /** 同一审核任务重复督办间隔（小时） */
    private Integer reviewEscalationIntervalHours;
    /** 是否启用管理端 API 请求/响应体加密 */
    private Boolean apiEncryptEnabled;
    /** 是否禁止管理端前端调试 */
    private Boolean disableFrontendDebug;
    /** 是否启用 API 请求签名 */
    private Boolean apiSignEnabled;

    /** 用户消息风暴阈值 */
    private Integer riskStormUserThreshold;
    /** 用户消息风暴窗口（秒） */
    private Integer riskStormUserWindowSeconds;
    /** 群风暴最低成员数 */
    private Integer riskStormGroupMinMembers;
    /** 大群成员数分界 */
    private Integer riskStormGroupLargeMembers;
    /** 中群每分钟上限 */
    private Integer riskStormGroupMidPerMinute;
    /** 大群每分钟上限 */
    private Integer riskStormGroupLargePerMinute;
    /** 中风险分数线 */
    private Integer riskScoreMediumMin;
    /** 高风险分数线 */
    private Integer riskScoreHighMin;
    /** 危急风险分数线 */
    private Integer riskScoreCriticalMin;
    /** 登录接口每分钟限流 */
    private Integer rateLimitLoginPerMinute;
    /** 注册接口每分钟限流 */
    private Integer rateLimitRegisterPerMinute;
    /** 搜索接口每分钟限流 */
    private Integer rateLimitSearchPerMinute;
    /** 列表接口每分钟限流 */
    private Integer rateLimitListPerMinute;
    /** 写接口每分钟限流 */
    private Integer rateLimitWritePerMinute;
    /** 上传接口每分钟限流 */
    private Integer rateLimitUploadPerMinute;

    /** SMTP 主机（空则沿用 env/yml） */
    private String mailHost;
    private Integer mailPort;
    private String mailUsername;
    private String mailPassword;
    private String mailFrom;
    private String mailFromName;
    private Boolean mailStartTls;
    private Boolean mailSsl;
    private Integer mailCodeExpireMinutes;

    private String mailTplRegisterSubject;
    private String mailTplRegisterHtml;
    private String mailTplResetSubject;
    private String mailTplResetHtml;
    private String mailTplWelcomeSubject;
    private String mailTplWelcomeHtml;

    /** 灵伴 AI 是否启用 */
    private Boolean linkmateEnabled;
    private String linkmateApiKey;
    private String linkmateBaseUrl;
    private String linkmateModel;
    private Integer linkmateMaxTokens;
    private Double linkmateTemperature;
    private Integer linkmateDailyTokenLimit;
    private String linkmateSystemPrompt;
    /** 当前模型是否支持深度思考（保存配置时自动检测） */
    private Boolean linkmateReasoningSupported;

    /** 语音转写（STT）独立 API Key；空则回退到灵伴 LLM Key */
    private String linkmateSttApiKey;
    /** 语音转写 API 基址；空则回退到灵伴 LLM 基址 */
    private String linkmateSttBaseUrl;
    /** 语音转写模型，如 whisper-1 */
    private String linkmateSttModel;

    private Long updateBy;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
