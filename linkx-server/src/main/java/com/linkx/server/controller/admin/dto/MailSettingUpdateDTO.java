package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "邮件 SMTP 配置更新")
public class MailSettingUpdateDTO {

    @NotBlank
    @Schema(description = "SMTP 主机")
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    @Schema(description = "SMTP 端口")
    private Integer port;

    @Schema(description = "SMTP 账号")
    private String username;

    @Schema(description = "SMTP 授权码；留空表示不修改")
    private String password;

    @NotBlank
    @Schema(description = "发件人地址")
    private String from;

    @Schema(description = "发件人显示名")
    private String fromName;

    @NotNull
    @Schema(description = "是否启用 STARTTLS")
    private Boolean startTls;

    @NotNull
    @Schema(description = "是否启用 SSL")
    private Boolean ssl;

    @NotNull
    @Min(1)
    @Max(1440)
    @Schema(description = "验证码有效分钟")
    private Integer codeExpireMinutes;
}
