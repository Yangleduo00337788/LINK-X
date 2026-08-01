package com.linkx.server.entity;

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
    /** 管理端登录失败最大次数 */
    private Integer adminLoginMaxAttempts;
    /** 管理端自动封禁分钟数 */
    private Integer adminLockDurationMinutes;
    /** 管理端是否强制开启 2FA */
    private Boolean adminTotpRequired;
    /** 客户端登录/注册验证码 */
    private Boolean clientCaptchaEnabled;
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
    /** 敏感词过滤总开关 */
    private Boolean sensitiveFilterEnabled;
    /** 客服邮箱 */
    private String supportEmail;
    /** 客服电话 */
    private String supportPhone;

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

    private Long updateBy;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
