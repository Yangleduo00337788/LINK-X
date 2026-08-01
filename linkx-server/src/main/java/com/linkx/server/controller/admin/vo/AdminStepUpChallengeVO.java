package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "二次验证挑战信息 / 可用方式")
public class AdminStepUpChallengeVO {

    @Schema(description = "可用验证方式：totp / email")
    private List<String> methods;

    @Schema(description = "是否已启用 TOTP")
    private Boolean totpEnabled;

    @Schema(description = "是否已绑定邮箱")
    private Boolean emailBound;

    @Schema(description = "掩码邮箱")
    private String emailMasked;

    @Schema(description = "需要二次验证的动作")
    private String action;

    @Schema(description = "当前请求的验证方式（request 接口返回）")
    private String method;

    @Schema(description = "验证码过期秒数（email 时）")
    private Long expiresIn;
}
