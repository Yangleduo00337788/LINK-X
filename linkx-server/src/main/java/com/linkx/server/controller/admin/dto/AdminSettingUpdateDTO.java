package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

@Data
@Schema(description = "系统配置统一更新（仅提交需变更的分组）")
public class AdminSettingUpdateDTO {

    @Valid
    @Schema(description = "注册配置")
    private RegisterSettingUpdateDTO register;

    @Valid
    @Schema(description = "登录配置")
    private LoginSettingUpdateDTO login;

    @Valid
    @Schema(description = "密码策略")
    private PasswordSettingUpdateDTO password;

    @Valid
    @Schema(description = "管理端配置")
    private AdminSideSettingUpdateDTO admin;

    @Valid
    @Schema(description = "客户端配置")
    private ClientSideSettingUpdateDTO client;

    @Valid
    @Schema(description = "邮件 SMTP 配置")
    private MailSettingUpdateDTO mail;

    @Valid
    @Schema(description = "邮件模板配置")
    private MailTemplateSettingUpdateDTO mailTemplates;

    @Valid
    @Schema(description = "安全配置")
    private SecuritySettingUpdateDTO security;
}
