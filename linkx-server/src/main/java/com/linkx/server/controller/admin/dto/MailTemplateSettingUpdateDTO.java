package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "邮件模板配置更新")
public class MailTemplateSettingUpdateDTO {

    @NotNull
    @Valid
    @Schema(description = "注册验证码邮件")
    private Template register;

    @NotNull
    @Valid
    @Schema(description = "重置密码邮件")
    private Template reset;

    @NotNull
    @Valid
    @Schema(description = "欢迎邮件")
    private Template welcome;

    @Data
    @Schema(description = "单封邮件模板")
    public static class Template {
        @Schema(description = "主题；空则恢复默认")
        private String subject;

        @Schema(description = "HTML 正文；空则恢复默认")
        private String html;
    }
}
