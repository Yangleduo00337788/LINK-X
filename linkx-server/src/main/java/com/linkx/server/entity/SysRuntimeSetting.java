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
    /** 客户端登录/注册验证码 */
    private Boolean clientCaptchaEnabled;
    /** 客户端是否开放注册 */
    private Boolean clientRegisterEnabled;
    /** 忘记密码邮箱验证是否启用 */
    private Boolean clientForgotPasswordEmailEnabled;

    private String appVersion;
    private String appChannel;
    private String releaseNotes;
    private String downloadUrl;
    private Long maxUploadBytes;
    private Long updateBy;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
