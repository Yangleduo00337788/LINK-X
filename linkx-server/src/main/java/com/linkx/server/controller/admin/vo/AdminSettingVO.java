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

    @Schema(description = "管理端配置（兼容旧字段，验证码请看 login）")
    private AdminSide admin;

    @Schema(description = "客户端配置")
    private ClientSide client;

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
        private Long maxUploadBytes;
    }
}
